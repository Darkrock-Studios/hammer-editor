package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Periodically evicts OG-image cache entries that haven't been requested within [MAX_AGE] and
 * re-enforces the size bound, so the cache reclaims disk for stories that stopped being shared.
 * The cache also self-bounds on write; this job handles age-based reclamation and drift.
 */
class OgImageCachePruneJob(
	private val ogImageService: OgImageService,
	logger: Logger,
) : RecurringTask("OG image cache prune job", logger) {

	override suspend fun tick() {
		ogImageService.prune(MAX_AGE)
	}

	override suspend fun nextDelay(): Duration = PRUNE_INTERVAL

	private companion object {
		val MAX_AGE = 30.days
		val PRUNE_INTERVAL = 12.hours
	}
}
