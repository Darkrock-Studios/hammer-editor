package com.darkrockstudios.apps.hammer.monitoring

import kotlinx.serialization.Serializable

/**
 * Admin-tunable configuration for the server monitoring subsystem. Persisted as
 * JSON in the `server_config` table via [com.darkrockstudios.apps.hammer.admin.AdminServerConfig.MONITORING_CONFIG].
 *
 * Defaults reflect the project's stance: collection is ON (the value is only
 * realized if the admin can actually see it), email alerting is OFF
 * (email may be unconfigured), and PII-sensitive data is short-lived via the
 * retention windows. [enabled] is the master kill-switch.
 */
@Serializable
data class MonitoringConfig(
	/** Master switch. When false, all collection no-ops and the UI hides the Monitoring section. */
	val enabled: Boolean = true,
	/** Record per-endpoint request counts / latency / error rate. */
	val trackApiMetrics: Boolean = true,
	/** Capture and deduplicate server-side errors. */
	val trackErrors: Boolean = true,
	/** Record login attempts for brute-force visibility. */
	val trackLoginAttempts: Boolean = true,
	/** Count unique readers of published / shared stories (cookieless, no stored IP). */
	val trackStoryReaders: Boolean = true,
	/** Store the source IP with login attempts (the most PII-sensitive bit). */
	val storeLoginIp: Boolean = true,
	/** How long rolled-up API metrics are retained (days). */
	val metricsRetentionDays: Int = 30,
	/** How long deduplicated errors are retained (days). */
	val errorRetentionDays: Int = 90,
	/** How long login attempts are retained (days). */
	val loginAttemptRetentionDays: Int = 30,
	/** Email the admin when alert thresholds are crossed. Off by default. */
	val alertEmailEnabled: Boolean = false,
	/** Destination address for monitoring alerts. */
	val alertEmail: String = "",
	/** Per-user sync-failure count within the alert window that triggers an alert. */
	val syncFailureThreshold: Int = 5,
)
