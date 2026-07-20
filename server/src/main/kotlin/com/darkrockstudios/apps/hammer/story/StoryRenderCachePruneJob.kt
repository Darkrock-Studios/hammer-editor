package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Periodically evicts story renders that haven't been read within [MAX_AGE] and re-enforces the
 * size bound, so the cache reclaims disk from stories that stopped being read — and from the
 * entries orphaned every time a story is edited. The cache also self-bounds on write; this job
 * handles age-based reclamation and drift.
 */
class StoryRenderCachePruneJob(
	private val storyRenderCache: StoryRenderCache,
	logger: Logger,
) : RecurringTask("Story render cache prune job", logger) {

	override suspend fun tick() {
		storyRenderCache.prune(MAX_AGE)
	}

	override suspend fun nextDelay(): Duration = PRUNE_INTERVAL

	private companion object {
		val MAX_AGE = 30.days
		val PRUNE_INTERVAL = 12.hours
	}
}
