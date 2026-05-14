package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class EntryAppearance(
	val entryId: Int,
	val name: String,
	val type: EntryType,
	val sceneCount: Int,
)

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
	// Stable per-day rollup of all sealed sessions across all devices. Key is
	// LocalDate.toString() ("yyyy-MM-dd") so it stays serializable.
	val dailyWordTotals: Map<String, Int> = emptyMap(),
	// Lifetime totals keyed by friendly device label.
	val wordsPerDevice: Map<String, Int> = emptyMap(),
	// Top-N pre-sorted, view-ready ranking of encyclopedia entries by scene reference count.
	val topAppearances: List<EntryAppearance> = emptyList(),
	// Sum of all entry->scene references across the project.
	val totalEntryConnections: Int = 0,
	// Word-count goal snapshot (null if user hasn't set one).
	val wordCountGoal: WordCountGoal? = null,
	val isDirty: Boolean = false,
	val lastCalculated: Instant,
	// Bump when the set of computed fields changes, so older caches are discarded
	// and recalculated rather than displayed with empty defaults.
	val schemaVersion: Int = 0,
) {
	companion object {
		const val CURRENT_SCHEMA_VERSION = 3
	}
}
