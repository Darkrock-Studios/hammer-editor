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

data class TotpEnrollment(
	/** Base32, for typing into an authenticator by hand. */
	val secret: String,
	val otpauthUri: String,
)

sealed interface ChallengeResult {
	data class Verified(val userId: Long) : ChallengeResult

	/** The challenge is still open (unless that was its last attempt). */
	data class InvalidCode(val userId: Long) : ChallengeResult

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

	suspend fun methods(userId: Long): List<String> =
		if (hasMfa(userId)) listOf(MfaMethod.TOTP, MfaMethod.RECOVERY) else emptyList()

	/** Checks a TOTP or recovery code, consuming it on success. */
	suspend fun verifyCode(userId: Long, code: String): Boolean {
		val totp = totpDao.get(userId)?.takeIf { it.confirmed } ?: return false
		val recovery = normalizeRecoveryCode(code)
		return if (recovery.length == RECOVERY_CODE_LENGTH && recovery.all { it in RECOVERY_ALPHABET }) {
			recoveryCodeDao.consume(userId, tokenHasher.hashTokenWithEveryKey(recovery), clock.now())
		} else {
			val secret = decryptSecret(totp) ?: return false
			val step = Totp.verify(secret, code, clock.now().epochSeconds, totp.last_step) ?: return false
			totpDao.advanceLastStep(userId, step)
		}
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
		return if (verifyCode(challenge.user_id, code)) {
			challengeDao.delete(challenge.id)
			ChallengeResult.Verified(challenge.user_id)
		} else {
			if (challenge.attempts >= MAX_CHALLENGE_ATTEMPTS) challengeDao.delete(challenge.id)
			ChallengeResult.InvalidCode(challenge.user_id)
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
		const val MAX_CHALLENGE_ATTEMPTS = 5
		val CHALLENGE_LIFETIME = 5.minutes
		private const val CHALLENGE_TOKEN_BYTES = 32
		private const val RECOVERY_CODE_COUNT = 8
		private const val RECOVERY_CODE_LENGTH = 10
		private const val RECOVERY_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
	}
}
