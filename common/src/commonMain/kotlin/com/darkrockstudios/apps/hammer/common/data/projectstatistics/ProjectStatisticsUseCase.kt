package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService

/**
 * Loads and recalculates the project statistics. Their entry appearance counts come from the
 * reference index, which this supplies so [StatisticsService] doesn't depend on
 * [ReferenceIndexService].
 */
class ProjectStatisticsUseCase(
	private val statisticsService: StatisticsService,
	private val referenceIndexService: ReferenceIndexService,
) {
	/** Cached statistics, recalculated first when the cache is missing, outdated or dirty. */
	suspend fun load(): ProjectStatistics = statisticsService.loadStatistics(referenceIndexService::loadIndex)

	/** Recalculates the reference index, then the statistics from it. */
	suspend fun recalculate(): ProjectStatistics {
		val index = referenceIndexService.recalculate()
		return statisticsService.recalculateStatistics { index }
	}
}
