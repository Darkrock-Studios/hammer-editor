package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Drives the monitoring background work on two cadences:
 *  - every [FLUSH_INTERVAL]: flush the in-memory [MetricsCollector] into HOUR
 *    buckets, and sync the collector's enabled flag from the live config;
 *  - every [MAINTENANCE_INTERVAL]: roll HOUR buckets into DAY buckets and purge
 *    everything past its retention window.
 *
 * The loop reads the live [MonitoringConfig] each tick, so the master toggle and
 * retention windows take effect at runtime without a restart.
 */
class MonitoringMaintenanceJob(
	private val configRepository: ConfigRepository,
	private val metricsRepository: MetricsRepository,
	private val errorRepository: ErrorRepository,
	private val securityRepository: SecurityRepository,
	private val collector: MetricsCollector,
	private val userActivityCollector: UserActivityCollector,
	private val userActivityRepository: UserActivityRepository,
	private val storyReaderCollector: StoryReaderCollector,
	private val storyReaderRepository: StoryReaderRepository,
	private val monitoringState: MonitoringState,
	private val emailService: EmailService,
	private val clock: Clock,
	private val logger: Logger,
) {
	private var job: Job? = null
	private var lastMaintenance: Instant = Instant.DISTANT_PAST

	// Per-subject cooldown for security alert emails. Single-coroutine access; resets on restart.
	private val securityAlertCooldown = mutableMapOf<String, Instant>()

	fun start(scope: CoroutineScope) {
		if (job?.isActive == true) {
			logger.info("Monitoring maintenance job already running")
			return
		}
		job = scope.launch {
			logger.info("Starting monitoring maintenance job")
			loop()
		}
	}

	fun stop() {
		job?.cancel()
		job = null
		logger.info("Monitoring maintenance job stopped")
	}

	fun isRunning(): Boolean = job?.isActive == true

	private suspend fun loop() {
		while (currentCoroutineContext().isActive) {
			try {
				tick()
			} catch (e: CancellationException) {
				throw e
				// Background loop must survive any tick failure.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Error in monitoring maintenance loop", e)
			}
			delay(FLUSH_INTERVAL)
		}
	}

	/** One scheduler tick. Public so tests can drive it deterministically. */
	suspend fun tick() {
		val config = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
		collector.setCollecting(config.enabled && config.trackApiMetrics)
		userActivityCollector.setCollecting(config.enabled)
		storyReaderCollector.setCollecting(config.enabled && config.trackStoryReaders)
		monitoringState.update(config)
		if (!config.enabled) return

		if (config.trackApiMetrics) flush()
		userActivityRepository.recordKeys(userActivityCollector.drainToKeys())
		storyReaderRepository.recordKeys(storyReaderCollector.drainToKeys())

		if (config.trackErrors && config.alertEmailEnabled && config.alertEmail.isNotBlank()) {
			evaluateErrorAlerts(config)
		}

		if (config.trackLoginAttempts && config.alertEmailEnabled && config.alertEmail.isNotBlank()) {
			evaluateSecurityAlerts(config)
		}

		val now = clock.now()
		if (now - lastMaintenance >= MAINTENANCE_INTERVAL) {
			runMaintenance(config, now)
			lastMaintenance = now
		}
	}

	/**
	 * Email the admin about error groups that have crossed the occurrence
	 * threshold and haven't been alerted on yet, then mark them notified so we
	 * don't repeat. Public for tests.
	 */
	suspend fun evaluateErrorAlerts(config: MonitoringConfig) {
		if (config.alertEmail.isBlank() || !emailService.isConfigured()) return

		val since = clock.now() - ALERT_WINDOW
		val toAlert = errorRepository.errorsToAlert(config.syncFailureThreshold, since)
		for (error in toAlert) {
			val subject = "[Hammer] ${error.exception_type} (${error.occurrence_count}×)" +
				(error.route?.let { " on $it" } ?: "")
			val text = buildString {
				appendLine("A monitored error has crossed the alert threshold on your Hammer server.")
				appendLine()
				appendLine("Type:        ${error.exception_type}")
				appendLine("Route:       ${error.route ?: "—"}")
				error.user_id?.let { appendLine("User:        $it") }
				appendLine("Occurrences: ${error.occurrence_count}")
				appendLine("Last seen:   ${error.last_seen}")
				appendLine("Message:     ${error.message ?: "—"}")
			}
			when (val result = emailService.sendEmail(config.alertEmail, subject, htmlBody(text), text)) {
				is EmailResult.Success -> errorRepository.markNotified(error.id)
				is EmailResult.Failure -> logger.error("Failed to send monitoring alert email: ${result.reason}")
			}
		}
	}

	/**
	 * Email the admin about brute-force signals crossing the [SecurityAlerts]
	 * thresholds, suppressing repeats per subject for [SECURITY_ALERT_COOLDOWN].
	 * Public for tests.
	 */
	suspend fun evaluateSecurityAlerts(config: MonitoringConfig) {
		if (config.alertEmail.isBlank() || !emailService.isConfigured()) return

		val now = clock.now()
		val since = now - SecurityAlerts.WINDOW
		val alerts = SecurityAlerts.derive(
			securityRepository.bruteForceEmails(since),
			securityRepository.bruteForceIps(since),
		)
		for (alert in alerts) {
			val until = securityAlertCooldown[alert.cooldownKey]
			if (until != null && now < until) continue

			// subject is an attacker-controlled login email/IP — strip CR/LF so it can't inject email headers.
			val safeSubject = alert.subject.replace(Regex("[\\r\\n]+"), " ").take(120)
			val subject = when (alert.kind) {
				SecurityAlertKind.ACCOUNT -> "[Hammer] Failed-login spike for $safeSubject"
				SecurityAlertKind.IP -> "[Hammer] Failed-login spike from $safeSubject"
			}
			val text = buildString {
				appendLine("A login-failure threshold was crossed on your Hammer server.")
				appendLine()
				when (alert.kind) {
					SecurityAlertKind.ACCOUNT -> appendLine("Account:   $safeSubject")
					SecurityAlertKind.IP -> appendLine("Source IP: $safeSubject")
				}
				appendLine("Detail:    ${alert.detail}")
				appendLine()
				appendLine("See the Security panel for the full list of recent attempts.")
			}
			when (val result = emailService.sendEmail(config.alertEmail, subject, htmlBody(text), text)) {
				is EmailResult.Success -> securityAlertCooldown[alert.cooldownKey] = now + SECURITY_ALERT_COOLDOWN
				is EmailResult.Failure -> logger.error("Failed to send security alert email: ${result.reason}")
			}
		}
		securityAlertCooldown.entries.removeAll { it.value <= now }
	}

	private fun htmlBody(text: String): String {
		val escaped = text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
		return "<pre style=\"font-family:monospace\">$escaped</pre>"
	}

	/** Persist whatever the collector has accumulated into HOUR buckets. */
	suspend fun flush() {
		for (delta in collector.drainToDeltas()) {
			metricsRepository.recordHourBucket(delta)
		}
	}

	/** Roll up + purge per the retention windows. Public for tests. */
	suspend fun runMaintenance() {
		val config = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
		if (!config.enabled) return
		runMaintenance(config, clock.now())
	}

	private suspend fun runMaintenance(config: MonitoringConfig, now: Instant) {
		if (config.trackApiMetrics) {
			metricsRepository.rollupAndTrim(
				hourCutoff = now - HOURLY_RESOLUTION_DAYS.days,
				dayCutoff = now - config.metricsRetentionDays.days,
			)
		}
		if (config.trackErrors) {
			errorRepository.purgeBefore(now - config.errorRetentionDays.days)
		}
		if (config.trackLoginAttempts) {
			securityRepository.purgeBefore(now - config.loginAttemptRetentionDays.days)
		}
		userActivityRepository.purgeBefore(now - config.metricsRetentionDays.days)
		storyReaderRepository.purgeBefore(now - config.metricsRetentionDays.days)
	}

	companion object {
		/** Keep hour-resolution buckets for this many days before folding them into daily buckets. */
		private const val HOURLY_RESOLUTION_DAYS = 7
		private val FLUSH_INTERVAL = 60.seconds
		private val MAINTENANCE_INTERVAL = 1.hours

		/** Only alert on error groups seen within this window. */
		private val ALERT_WINDOW = 24.hours

		/** Per-subject suppression after a security alert email, so a sustained attack emails at most once per window. */
		private val SECURITY_ALERT_COOLDOWN = 6.hours
	}
}
