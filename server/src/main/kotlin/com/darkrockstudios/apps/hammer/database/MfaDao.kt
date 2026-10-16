package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class AccountTotpDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.accountTotpQueries

	suspend fun get(userId: Long): AccountTotp? = withContext(ioDispatcher) {
		queries.getByUserId(userId).executeAsOneOrNull()
	}

	/** Replaces a pending enrollment; leaves a confirmed one untouched. */
	suspend fun upsertPending(userId: Long, secret: String, cipher: String) = withContext(ioDispatcher) {
		queries.transaction {
			queries.deletePending(userId)
			queries.insertPending(userId = userId, secret = secret, cipher = cipher)
		}
	}

	/** False when there is no pending enrollment, or [step] is not after the last one. */
	suspend fun confirm(userId: Long, step: Long): Boolean = withContext(ioDispatcher) {
		queries.confirm(step = step, userId = userId).executeAsOneOrNull() != null
	}

	/** False when [step] was already used, so a code cannot be replayed. */
	suspend fun advanceLastStep(userId: Long, step: Long): Boolean = withContext(ioDispatcher) {
		queries.advanceLastStep(step = step, userId = userId).executeAsOneOrNull() != null
	}

	/** The failure count after this one, or null when the account has no confirmed TOTP. */
	suspend fun recordFailure(userId: Long, now: Instant, windowCutoff: Instant): Int? = withContext(ioDispatcher) {
		queries.recordFailure(windowCutoff = windowCutoff, now = now, userId = userId).executeAsOneOrNull()
	}

	suspend fun lock(userId: Long, until: Instant) = withContext(ioDispatcher) {
		queries.lock(until = until, userId = userId)
	}

	suspend fun clearFailures(userId: Long) = withContext(ioDispatcher) {
		queries.clearFailures(userId)
	}

	suspend fun delete(userId: Long) = withContext(ioDispatcher) {
		queries.deleteAllForUser(userId)
	}
}

class AccountRecoveryCodeDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val db = database.serverDatabase
	private val queries = db.accountRecoveryCodeQueries

	/** Replaces every code the account had. */
	suspend fun replaceAll(userId: Long, codeHashes: List<String>) = withContext(ioDispatcher) {
		queries.transaction {
			queries.deleteAllForUser(userId)
			codeHashes.forEach { queries.insertCode(userId, it) }
		}
	}

	/** [codeHashes] are one code hashed under each key generation. */
	suspend fun consume(userId: Long, codeHashes: List<String>, usedAt: Instant): Boolean = withContext(ioDispatcher) {
		queries.consume(usedAt = usedAt, userId = userId, codeHashes = codeHashes).executeAsOneOrNull() != null
	}

	suspend fun countUnused(userId: Long): Long = withContext(ioDispatcher) {
		queries.countUnused(userId).executeAsOne()
	}

	suspend fun deleteAll(userId: Long) = withContext(ioDispatcher) {
		queries.deleteAllForUser(userId)
	}
}

class MfaLoginChallengeDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.mfaLoginChallengeQueries

	suspend fun create(userId: Long, installId: String, tokenHash: String, expires: Instant) =
		withContext(ioDispatcher) {
			queries.createChallenge(userId = userId, installId = installId, tokenHash = tokenHash, expires = expires)
		}

	/**
	 * Counts one attempt; null when the challenge is unknown, issued to another install,
	 * expired, or out of attempts.
	 */
	suspend fun claimAttempt(tokenHash: String, installId: String, now: Instant, maxAttempts: Int): MfaLoginChallenge? =
		withContext(ioDispatcher) {
			queries.claimAttempt(
				tokenHash = tokenHash,
				installId = installId,
				now = now,
				maxAttempts = maxAttempts,
			).executeAsOneOrNull()
		}

	suspend fun delete(id: Long) = withContext(ioDispatcher) {
		queries.deleteById(id)
	}

	suspend fun deleteAllForUser(userId: Long) = withContext(ioDispatcher) {
		queries.deleteAllForUser(userId)
	}

	suspend fun deleteExpiredBefore(cutoff: Instant) = withContext(ioDispatcher) {
		queries.deleteExpiredBefore(cutoff)
	}
}
