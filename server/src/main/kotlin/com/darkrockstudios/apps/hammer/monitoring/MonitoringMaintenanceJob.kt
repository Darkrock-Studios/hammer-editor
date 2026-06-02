package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
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
import kotlin.time.Duration.Companion.minutes

/**
 * Periodically rolls up and purges monitoring data according to the configured
 * retention windows. Modeled on PatreonPollingJob: the loop reads the live
 * [MonitoringConfig] each tick, so the master toggle and retention windows take
 * effect at runtime without a restart.
 *
 * (The collector flush that feeds HOUR buckets is added in Phase 2; this job
 * already correctly rolls up and trims whatever data exists.)
 */
class MonitoringMaintenanceJob(
	private val configRepository: ConfigRepository,
	private val metricsRepository: MetricsRepository,
	private val errorRepository: ErrorRepository,
	private val securityRepository: SecurityRepository,
	private val clock: Clock,
	private val logger: Logger,
) {
	private var job: Job? = null

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
				runMaintenance()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				logger.error("Error in monitoring maintenance loop", e)
			}
			delay(MAINTENANCE_INTERVAL)
		}
	}

	/** Single maintenance pass. Public so it can be driven directly from tests. */
	suspend fun runMaintenance() {
		val config = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
		if (!config.enabled) return

		val now = clock.now()

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
		private val MAINTENANCE_INTERVAL = 60.minutes
	}
}
