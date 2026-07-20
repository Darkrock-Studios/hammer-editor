package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Periodically evicts entries no [PrunableCache] has served within [MAX_AGE] and re-enforces each
 * cache's size bound, reclaiming disk from work nobody asks for any more. The caches also self-bound
 * on write; this job handles age-based reclamation and drift.
 *
 * One job for every cache: retention policy lives in exactly one place, and a new cache only has to
 * be added to the injected list.
 */
class DiskCachePruneJob(
	private val caches: List<PrunableCache>,
	logger: Logger,
) : RecurringTask("Disk cache prune job", logger) {

	override suspend fun tick() {
		withContext(Dispatchers.IO) {
			for (cache in caches) {
				// One cache's failure must not stop the others from being swept.
				runCatching { cache.prune(MAX_AGE) }
					.onFailure { logger.warn("Failed to prune $cache", it) }
			}
		}
	}

	override suspend fun nextDelay(): Duration = PRUNE_INTERVAL

	private companion object {
		val MAX_AGE = 30.days
		val PRUNE_INTERVAL = 12.hours
	}
}
