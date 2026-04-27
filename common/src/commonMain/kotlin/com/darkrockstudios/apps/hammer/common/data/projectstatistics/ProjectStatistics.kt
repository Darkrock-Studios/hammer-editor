package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectStatistics(
	val numberOfScenes: Int,
	val totalWords: Int,
	val wordsByChapter: Map<String, Int>,
	val encyclopediaEntriesByType: Map<String, Int>,
	val longestSceneName: String? = null,
	val longestSceneWords: Int = 0,
	val shortestSceneWords: Int = 0,
	val medianSceneWords: Int = 0,
	val sceneWordsStdDev: Int = 0,
	val numberOfNotes: Int = 0,
	val numberOfTimelineEvents: Int = 0,
	val isDirty: Boolean = false,
	val lastCalculated: Instant,
	// Bump when the set of computed fields changes, so older caches are discarded
	// and recalculated rather than displayed with empty defaults.
	val schemaVersion: Int = 0,
) {
	companion object {
		const val CURRENT_SCHEMA_VERSION = 2
	}
}
