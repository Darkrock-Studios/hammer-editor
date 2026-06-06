package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import kotlinx.coroutines.*
import org.slf4j.Logger
import kotlin.time.Duration.Companion.minutes

class PatreonPollingJob(
	private val patreonSyncService: PatreonSyncService,
	private val configRepository: ConfigRepository,
	private val logger: Logger
) {
	private var pollingJob: Job? = null

	fun start(scope: CoroutineScope) {
		if (pollingJob?.isActive == true) {
			logger.info("Patreon polling job already running")
			return
		}

		pollingJob = scope.launch {
			logger.info("Starting Patreon polling job")
			pollLoop()
		}
	}

	fun stop() {
		pollingJob?.cancel()
		pollingJob = null
		logger.info("Patreon polling job stopped")
	}

	private suspend fun pollLoop() {
		while (currentCoroutineContext().isActive) {
			try {
				val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)

				if (config.enabled) {
					logger.info("Running scheduled Patreon sync")
					val result = patreonSyncService.performFullSync()

					if (result.isFailure) {
						logger.error("Scheduled Patreon sync failed", result.exceptionOrNull())
					}
				}

				val intervalMinutes = config.pollIntervalMinutes.coerceAtLeast(1)
				delay(intervalMinutes.minutes)
			} catch (e: CancellationException) {
				throw e
				// Background loop must survive any sync failure.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Error in Patreon polling loop", e)
				// Wait a bit before retrying on error
				delay(5.minutes)
			}
		}
	}

	fun isRunning(): Boolean = pollingJob?.isActive == true
}
