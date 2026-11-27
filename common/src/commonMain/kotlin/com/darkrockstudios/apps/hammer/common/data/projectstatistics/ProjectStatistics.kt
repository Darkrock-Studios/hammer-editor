package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class EntryAppearance(
	val entryId: Int,
	val name: String,
	val type: EntryType,
	val sceneCount: Int,
)

/** One author's lifetime words, with their devices' shares keyed by device label. */
@Serializable
data class AuthorWords(
	val authorId: Long?,
	val authorName: String?,
	val words: Int,
	val devices: Map<String, Int>,
)

@Serializable
data class ProjectStatistics(
	val numberOfScenes: Int,
	val totalWords: Int,
	val wordsByChapter: Map<Int, Int>,
	val encyclopediaEntriesByType: Map<String, Int>,
	val longestSceneId: Int? = null,
	val longestSceneName: String? = null,
	val longestSceneWords: Int = 0,
	val lastEditedSceneId: Int? = null,
	val lastEditedSceneName: String? = null,
	val lastEditedAt: Instant? = null,
	val shortestSceneWords: Int = 0,
	val medianSceneWords: Int = 0,
	val sceneWordsStdDev: Int = 0,
	val numberOfNotes: Int = 0,
	val numberOfTimelineEvents: Int = 0,
	// Words per day from the sessions of [viewerId], on any device; with no account, every
	// session. Key is LocalDate.toString() ("yyyy-MM-dd") so it stays serializable.
	val dailyWordTotals: Map<String, Int> = emptyMap(),
	// Lifetime totals of the same sessions keyed by friendly device label.
	val wordsPerDevice: Map<String, Int> = emptyMap(),
	// Every author's words; empty unless more than one author has written in this project.
	val wordsPerAuthor: List<AuthorWords> = emptyList(),
	// Top-N pre-sorted, view-ready ranking of encyclopedia entries by scene reference count.
	val topAppearances: List<EntryAppearance> = emptyList(),
	// Sum of all entry->scene references across the project.
	val totalEntryConnections: Int = 0,
	// Word-count goal snapshot (null if user hasn't set one).
	val wordCountGoal: WordCountGoal? = null,
	// The signed-in account the writing activity totals are for; null with none.
	val viewerId: Long? = null,
	val isDirty: Boolean = false,
	val lastCalculated: Instant,
	// Bump when the set of computed fields changes, so older caches are discarded
	// and recalculated rather than displayed with empty defaults.
	val schemaVersion: Int = 0,
	// The project these were calculated from: one replaced at the same path reuses the cache folder.
	val projectId: String? = null,
) {
	companion object {
		const val CURRENT_SCHEMA_VERSION = 8
	}
}
