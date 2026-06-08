package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.GetBruteForceEmails
import com.darkrockstudios.apps.hammer.GetBruteForceIps
import kotlin.time.Duration.Companion.hours

enum class SecurityAlertKind { ACCOUNT, IP }

/**
 * A brute-force / credential-stuffing signal derived from the recent
 * login-failure window. Rendered on the Overview page and (when configured)
 * emailed to the admin — both go through [SecurityAlerts.derive] so the UI and
 * the email path always agree on what counts as an alert.
 */
data class SecurityAlert(
	val kind: SecurityAlertKind,
	/** The account email (ACCOUNT) or source IP (IP) the alert concerns. */
	val subject: String,
	val detail: String,
) {
	/** Stable key for de-duplicating repeat emails about the same subject. */
	val cooldownKey: String get() = "${kind.name}:$subject"
}

object SecurityAlerts {
	/** Tight "happening now" window; concentration thresholds keep forgotten-password noise quiet. */
	val WINDOW = 1.hours
	const val ACCOUNT_FAILURES = 10L  // one account hammered
	const val IP_FAILURES = 20L       // one IP, high failure volume
	const val IP_ACCOUNTS = 6L        // one IP spraying many accounts

	/** Maps already-breaching rollups (thresholds applied in-query) into alerts. */
	fun derive(
		bruteForceEmails: List<GetBruteForceEmails>,
		bruteForceIps: List<GetBruteForceIps>,
	): List<SecurityAlert> {
		val accountAlerts = bruteForceEmails.mapNotNull { f ->
			val email = f.email ?: return@mapNotNull null
			SecurityAlert(
				kind = SecurityAlertKind.ACCOUNT,
				subject = email,
				detail = "${f.failures} failed logins for this account in the last hour",
			)
		}
		val ipAlerts = bruteForceIps.mapNotNull { f ->
			val ip = f.ip_address ?: return@mapNotNull null
			val accounts = if (f.accounts == 1L) "1 account" else "${f.accounts} accounts"
			SecurityAlert(
				kind = SecurityAlertKind.IP,
				subject = ip,
				detail = "${f.failures} failed logins across $accounts in the last hour",
			)
		}
		return accountAlerts + ipAlerts
	}
}
