package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.Login_attempt
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import io.ktor.util.*
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

/**
 * Records login attempts for brute-force visibility. Emails are stored
 * lower-cased so failure counts are stable regardless of how the attacker cased
 * the address. The IP is optional — callers pass null when storeLoginIp is off.
 */
class SecurityRepository(
	private val loginAttemptDao: LoginAttemptDao,
	private val clock: Clock,
) : KoinComponent {

	suspend fun recordLoginAttempt(email: String?, ipAddress: String?, success: Boolean) {
		loginAttemptDao.recordAttempt(
			email = email?.cleaned(),
			ipAddress = ipAddress,
			success = success,
			at = clock.now(),
		)
	}

	suspend fun countRecentFailures(email: String, since: Instant): Long =
		loginAttemptDao.countRecentFailuresByEmail(email.cleaned(), since)

	suspend fun getRecentAttempts(page: Int, pageSize: Int): List<Login_attempt> =
		loginAttemptDao.getRecentAttempts(pageSize.toLong(), (page.toLong() * pageSize))

	/** Accounts with the most failed logins since [since]. */
	suspend fun getTopFailingEmails(since: Instant, limit: Int = 10): List<com.darkrockstudios.apps.hammer.GetTopFailingEmails> =
		loginAttemptDao.getTopFailingEmails(since, limit.toLong())

	suspend fun purgeBefore(cutoff: Instant) = loginAttemptDao.deleteAttemptsBefore(cutoff)

	private fun String.cleaned(): String = trim().toLowerCasePreservingASCIIRules()
}
