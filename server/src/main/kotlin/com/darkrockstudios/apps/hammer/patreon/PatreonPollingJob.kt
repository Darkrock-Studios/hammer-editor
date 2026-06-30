package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import org.slf4j.Logger
import kotlin.time.Duration.Companion.minutes

class PatreonPollingJob(
	private val patreonSyncService: PatreonSyncService,
	private val configRepository: ConfigRepository,
	logger: Logger,
) : RecurringTask("Patreon polling job", logger) {

	override suspend fun tick() {
		val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		if (config.enabled) {
			logger.info("Running scheduled Patreon sync")
			val result = patreonSyncService.performFullSync()
			if (result.isFailure) {
				logger.error("Scheduled Patreon sync failed", result.exceptionOrNull())
			}
		}
	}

	override suspend fun nextDelay() =
		configRepository.get(AdminServerConfig.PATREON_CONFIG).pollIntervalMinutes.coerceAtLeast(1).minutes

	/** Back off longer after a failure (e.g. a sync error or an unreadable config) before retrying. */
	override suspend fun errorBackoff() = 5.minutes
}
