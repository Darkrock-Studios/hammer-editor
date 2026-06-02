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
	private val monitoringState: MonitoringState,
	private val emailService: EmailService,
	private val clock: Clock,
	private val logger: Logger,
) {
	private var job: Job? = null
	private var lastMaintenance: Instant = Instant.DISTANT_PAST

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
			} catch (e: Exception) {
				logger.error("Error in monitoring maintenance loop", e)
			}
			delay(FLUSH_INTERVAL)
		}
	}

	/** One scheduler tick. Public so tests can drive it deterministically. */
	suspend fun tick() {
		val config = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
		collector.setCollecting(config.enabled && config.trackApiMetrics)
		monitoringState.update(config)
		if (!config.enabled) return

		if (config.trackApiMetrics) flush()

		if (config.trackErrors && config.alertEmailEnabled && config.alertEmail.isNotBlank()) {
			evaluateErrorAlerts(config)
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
	}

	companion object {
		/** Keep hour-resolution buckets for this many days before folding them into daily buckets. */
		private const val HOURLY_RESOLUTION_DAYS = 7
		private val FLUSH_INTERVAL = 60.seconds
		private val MAINTENANCE_INTERVAL = 1.hours

		/** Only alert on error groups seen within this window. */
		private val ALERT_WINDOW = 24.hours
	}
}
