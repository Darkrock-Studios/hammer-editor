package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Login_attempt
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

open class LoginAttemptDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.loginAttemptQueries

	open suspend fun recordAttempt(
		email: String?,
		ipAddress: String?,
		success: Boolean,
		at: Instant,
	): Unit = withContext(ioDispatcher) {
		queries.recordAttempt(email, ipAddress, success, at)
	}

	open suspend fun countRecentFailuresByEmail(email: String, since: Instant): Long = withContext(ioDispatcher) {
		queries.countRecentFailuresByEmail(email, since).executeAsOne()
	}

	open suspend fun getRecentAttempts(limit: Long, offset: Long): List<Login_attempt> = withContext(ioDispatcher) {
		queries.getRecentAttempts(limit, offset).executeAsList()
	}

	open suspend fun getTopFailingEmails(since: Instant, limit: Long): List<com.darkrockstudios.apps.hammer.GetTopFailingEmails> =
		withContext(ioDispatcher) {
			queries.getTopFailingEmails(since, limit).executeAsList()
		}

	open suspend fun getBruteForceEmails(since: Instant, failThreshold: Long): List<com.darkrockstudios.apps.hammer.GetBruteForceEmails> =
		withContext(ioDispatcher) {
			queries.getBruteForceEmails(since, failThreshold).executeAsList()
		}

	open suspend fun getBruteForceIps(since: Instant, failThreshold: Long, accountThreshold: Long): List<com.darkrockstudios.apps.hammer.GetBruteForceIps> =
		withContext(ioDispatcher) {
			queries.getBruteForceIps(since, failThreshold, accountThreshold).executeAsList()
		}

	open suspend fun deleteAttemptsBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.deleteAttemptsBefore(cutoff)
	}
}
