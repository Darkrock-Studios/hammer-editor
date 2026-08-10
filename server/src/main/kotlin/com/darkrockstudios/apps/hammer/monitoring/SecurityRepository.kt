package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.Login_attempt
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import io.ktor.util.*
import io.ktor.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

/**
 * Records login attempts for brute-force visibility. Emails are stored
 * lower-cased so failure counts are stable regardless of how the attacker cased
 * the address, and blank ones as null so they stay out of the per-account
 * queries. The monitoring toggles are honored here rather than at the call
 * sites, so callers always pass the source address and no login route can
 * record what an operator has switched off.
 */
class SecurityRepository(
	private val loginAttemptDao: LoginAttemptDao,
	private val monitoringState: MonitoringState,
	private val log: Logger,
	private val clock: Clock,
) : KoinComponent {

	/**
	 * Recording is passive observation, so a failed write is logged and swallowed
	 * rather than failing the sign-in that triggered it.
	 */
	suspend fun recordLoginAttempt(email: String?, ipAddress: String?, success: Boolean) {
		if (monitoringState.loginTrackingEnabled.not()) return

		try {
			loginAttemptDao.recordAttempt(
				email = email?.ifBlank { null }?.cleaned(),
				ipAddress = ipAddress.takeIf { monitoringState.storeLoginIp },
				success = success,
				at = clock.now(),
			)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			log.error("Failed to record login attempt", e)
		}
	}

	suspend fun countRecentFailures(email: String, since: Instant): Long =
		loginAttemptDao.countRecentFailuresByEmail(email.cleaned(), since)

	suspend fun getRecentAttempts(page: Int, pageSize: Int): List<Login_attempt> =
		loginAttemptDao.getRecentAttempts(pageSize.toLong(), (page.toLong() * pageSize))

	/** Accounts with the most failed logins since [since]. */
	suspend fun getTopFailingEmails(since: Instant, limit: Int = 10): List<com.darkrockstudios.apps.hammer.GetTopFailingEmails> =
		loginAttemptDao.getTopFailingEmails(since, limit.toLong())

	/** Accounts crossing the brute-force failure threshold since [since]. */
	suspend fun bruteForceEmails(since: Instant): List<com.darkrockstudios.apps.hammer.GetBruteForceEmails> =
		loginAttemptDao.getBruteForceEmails(since, SecurityAlerts.ACCOUNT_FAILURES)

	/** IPs crossing a brute-force threshold (failure volume or distinct accounts) since [since]. */
	suspend fun bruteForceIps(since: Instant): List<com.darkrockstudios.apps.hammer.GetBruteForceIps> =
		loginAttemptDao.getBruteForceIps(since, SecurityAlerts.IP_FAILURES, SecurityAlerts.IP_ACCOUNTS)

	suspend fun purgeBefore(cutoff: Instant) = loginAttemptDao.deleteAttemptsBefore(cutoff)

	private fun String.cleaned(): String = trim().toLowerCasePreservingASCIIRules()
}
