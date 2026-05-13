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
enum class TagSource { SCENE, NOTE, ENCYCLOPEDIA, EVENT }

/**
 * TOML keys must be primitive, so the per-source counts are keyed by [TagSource.name].
 * Use [getCount] to read by enum.
 */
@Serializable
data class TagFrequency(
	val name: String,
	val countBySource: Map<String, Int>,
) {
	val total: Int get() = countBySource.values.sum()
	val breadth: Int get() = countBySource.count { (_, v) -> v > 0 }
	fun getCount(source: TagSource): Int = countBySource[source.name] ?: 0
}

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
	// All project tags ranked desc by total usage across scenes/notes/encyclopedia/events.
	val tagFrequencies: List<TagFrequency> = emptyList(),
	// Total tag uses per source (keyed by TagSource.name for TOML compatibility).
	val tagUsesBySource: Map<String, Int> = emptyMap(),
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
