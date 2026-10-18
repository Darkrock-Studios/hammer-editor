package com.darkrockstudios.apps.hammer.account.mfa

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AccountRecoveryCodeDao
import com.darkrockstudios.apps.hammer.database.AccountTotp
import com.darkrockstudios.apps.hammer.database.AccountTotpDao
import com.darkrockstudios.apps.hammer.database.MfaLoginChallengeDao
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import java.net.URI
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.slf4j.LoggerFactory

data class TotpEnrollment(
	/** Base32, for typing into an authenticator by hand. */
	val secret: String,
	val otpauthUri: String,
)

sealed interface CodeResult {
	data object Verified : CodeResult
	data object Invalid : CodeResult

	/** Too many wrong codes; nothing is checked until [until]. */
	data class Locked(val until: Instant) : CodeResult
}

sealed interface ChallengeResult {
	data class Verified(val userId: Long) : ChallengeResult

	/** The challenge is still open (unless that was its last attempt). */
	data class InvalidCode(val userId: Long) : ChallengeResult

	/** Too many wrong codes across the account's challenges; see [CodeResult.Locked]. */
	data class Locked(val userId: Long, val until: Instant) : ChallengeResult

	/** Unknown, expired, or out of attempts. */
	data object Invalid : ChallengeResult
}

class MfaRepository(
	private val accountDao: AccountDao,
	private val totpDao: AccountTotpDao,
	private val recoveryCodeDao: AccountRecoveryCodeDao,
	private val challengeDao: MfaLoginChallengeDao,
	private val encryptor: ContentEncryptor,
	private val encryptorRegistry: ContentEncryptorRegistry,
	private val tokenHasher: TokenHasher,
	private val serverConfig: ServerConfig,
	private val random: SecureRandom,
	private val base64: Base64,
	private val clock: Clock,
) {
	/** Null when the account is missing or already has confirmed TOTP. */
	suspend fun beginTotp(userId: Long): TotpEnrollment? {
		val account = accountDao.getAccount(userId) ?: return null
		if (totpDao.get(userId)?.confirmed == true) return null

		val secret = Totp.newSecret()
		val encoded = Totp.base32Encode(secret)
		totpDao.upsertPending(userId, encryptor.encrypt(encoded, account.cipher_secret), encryptor.cipherName())
		// A confirm racing this call may have won; its secret stays.
		if (totpDao.get(userId)?.confirmed != false) return null

		return TotpEnrollment(encoded, Totp.otpauthUri(secret, issuer(), account.email))
	}

	/** Returns the new recovery codes, or null when [code] does not match the pending secret. */
	suspend fun confirmTotp(userId: Long, code: String): List<String>? {
		val row = totpDao.get(userId)?.takeIf { !it.confirmed } ?: return null
		val secret = decryptSecret(row) ?: return null
		val step = Totp.verify(secret, code, clock.now().epochSeconds, lastStep = null) ?: return null
		if (!totpDao.confirm(userId, step)) return null
		return regenerateRecoveryCodes(userId)
	}

	suspend fun disableTotp(userId: Long) {
		totpDao.delete(userId)
		recoveryCodeDao.deleteAll(userId)
		challengeDao.deleteAllForUser(userId)
	}

	suspend fun regenerateRecoveryCodes(userId: Long): List<String> {
		val codes = List(RECOVERY_CODE_COUNT) { newRecoveryCode() }
		recoveryCodeDao.replaceAll(userId, codes.map { tokenHasher.hashToken(normalizeRecoveryCode(it)) })
		return codes
	}

	suspend fun hasMfa(userId: Long): Boolean = totpDao.get(userId)?.confirmed == true

	/** When the account is refusing codes, the instant that ends; null otherwise. */
	suspend fun lockedUntil(userId: Long): Instant? =
		totpDao.get(userId)?.takeIf { it.confirmed }?.locked_until?.takeIf { it > clock.now() }

	suspend fun methods(userId: Long): List<String> =
		if (hasMfa(userId)) listOf(MfaMethod.TOTP, MfaMethod.RECOVERY) else emptyList()

	/**
	 * Checks a TOTP or recovery code, consuming it on success. Wrong codes count against the
	 * account as a whole, not just one challenge, so a stolen password cannot be turned into an
	 * unbounded guess at the second factor.
	 */
	suspend fun verifyCode(userId: Long, code: String): CodeResult {
		val totp = totpDao.get(userId)?.takeIf { it.confirmed } ?: return CodeResult.Invalid
		val now = clock.now()
		totp.locked_until?.takeIf { it > now }?.let { return CodeResult.Locked(it) }

		val recovery = normalizeRecoveryCode(code)
		val verified = if (recovery.length == RECOVERY_CODE_LENGTH && recovery.all { it in RECOVERY_ALPHABET }) {
			recoveryCodeDao.consume(userId, tokenHasher.hashTokenWithEveryKey(recovery), now)
		} else {
			val secret = decryptSecret(totp) ?: return CodeResult.Invalid
			val step = Totp.verify(secret, code, now.epochSeconds, lastStep = null) ?: return recordFailure(userId, now)
			// A replay of a code already accepted is refused, but it was not a guess.
			if (step <= totp.last_step || !totpDao.advanceLastStep(userId, step)) return CodeResult.Invalid
			true
		}

		return if (verified) {
			if (totp.failed_attempts > 0 || totp.locked_until != null) totpDao.clearFailures(userId)
			CodeResult.Verified
		} else {
			recordFailure(userId, now)
		}
	}

	private suspend fun recordFailure(userId: Long, now: Instant): CodeResult {
		val failures = totpDao.recordFailure(userId, now, windowCutoff = now - FAILURE_WINDOW) ?: return CodeResult.Invalid
		if (failures < MAX_FAILED_CODES) return CodeResult.Invalid
		val until = now + LOCKOUT
		totpDao.lock(userId, until)
		log.warn("MFA locked for user $userId after $failures wrong codes, until $until")
		return CodeResult.Locked(until)
	}

	suspend fun createChallenge(userId: Long, installId: String): MfaChallenge {
		val token = base64.encode(ByteArray(CHALLENGE_TOKEN_BYTES).also(random::nextBytes))
		challengeDao.create(userId, installId, tokenHasher.hashToken(token), clock.now() + CHALLENGE_LIFETIME)
		return MfaChallenge(token, methods(userId))
	}

	suspend fun verifyChallenge(token: String, installId: String, code: String): ChallengeResult {
		val challenge = challengeDao.claimAttempt(
			tokenHasher.hashToken(token),
			installId,
			clock.now(),
			MAX_CHALLENGE_ATTEMPTS,
		) ?: return ChallengeResult.Invalid
		return when (val result = verifyCode(challenge.user_id, code)) {
			CodeResult.Verified -> {
				challengeDao.delete(challenge.id)
				ChallengeResult.Verified(challenge.user_id)
			}

			is CodeResult.Locked -> ChallengeResult.Locked(challenge.user_id, result.until)
			CodeResult.Invalid -> {
				if (challenge.attempts >= MAX_CHALLENGE_ATTEMPTS) challengeDao.delete(challenge.id)
				ChallengeResult.InvalidCode(challenge.user_id)
			}
		}
	}

	/** Every factor, for an admin unlocking an account. */
	suspend fun resetAll(userId: Long) = disableTotp(userId)

	suspend fun purgeExpiredChallenges() {
		challengeDao.deleteExpiredBefore(clock.now())
	}

	private suspend fun decryptSecret(row: AccountTotp): ByteArray? {
		val account = accountDao.getAccount(row.user_id) ?: return null
		val encoded = encryptorRegistry.resolve(row.cipher).decrypt(row.secret, account.cipher_secret)
		return Totp.base32Decode(encoded)
	}

	private fun newRecoveryCode(): String {
		val chars = CharArray(RECOVERY_CODE_LENGTH) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
		val half = RECOVERY_CODE_LENGTH / 2
		return "${chars.concatToString(0, half)}-${chars.concatToString(half, RECOVERY_CODE_LENGTH)}"
	}

	private fun normalizeRecoveryCode(code: String): String =
		code.lowercase().filterNot { it == '-' || it.isWhitespace() }

	private fun issuer(): String {
		val host = serverConfig.publicUrl
			?.let { runCatching { URI(it).host }.getOrNull() }
			?: serverConfig.host
		return "Hammer ($host)"
	}

	companion object {
		private val log = LoggerFactory.getLogger(MfaRepository::class.java)

		const val MAX_CHALLENGE_ATTEMPTS = 5
		val CHALLENGE_LIFETIME = 5.minutes

		/** Wrong codes per account within [FAILURE_WINDOW] before codes are refused for [LOCKOUT]. */
		const val MAX_FAILED_CODES = 10
		val FAILURE_WINDOW = 15.minutes
		val LOCKOUT = 15.minutes
		private const val CHALLENGE_TOKEN_BYTES = 32
		private const val RECOVERY_CODE_COUNT = 8
		private const val RECOVERY_CODE_LENGTH = 10
		private const val RECOVERY_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
	}
}
