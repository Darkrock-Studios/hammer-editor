package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationContext
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal fun statsOperations(): List<Operation<*, *>> = listOf(
	operation<StatsProjectInput, ProjectStats>(
		name = "stats.project",
		description = "A project's statistics, as its Home screen shows them.",
		access = Access.Read,
		agentVisible = true,
	) { input ->
		projects.withProject(input.project) { project ->
			val service = project.scope.get<StatisticsService>()
			val stats = if (input.recalculate) service.recalculateStatistics() else service.loadStatistics()
			val scenes = project.scope.get<SceneEditorService>()
			ProjectStats(
				totalWords = stats.totalWords,
				sceneCount = stats.numberOfScenes,
				chapters = scenes.getSceneTree().root.children.mapNotNull { node ->
					stats.wordsByChapter[node.value.id]?.let { ChapterWords(node.value.id, node.value.name, it) }
				},
				longestScene = stats.longestSceneId?.let {
					SceneWords(it, stats.longestSceneName.orEmpty(), stats.longestSceneWords)
				},
				shortestSceneWords = stats.shortestSceneWords,
				medianSceneWords = stats.medianSceneWords,
				noteCount = stats.numberOfNotes,
				timelineEventCount = stats.numberOfTimelineEvents,
				entryCounts = EntryType.entries.map {
					EntryCount(EntryKind.of(it), stats.encyclopediaEntriesByType[it.name] ?: 0)
				},
				topAppearances = stats.topAppearances.map {
					Appearance(it.entryId, it.name, EntryKind.of(it.type), it.sceneCount)
				},
				wordCountGoal = stats.wordCountGoal?.let(::WordGoal),
				lastEdited = stats.lastEditedAt,
				lastEditedScene = stats.lastEditedSceneId?.let { SceneRef(it, stats.lastEditedSceneName.orEmpty()) },
			)
		}
	},
	operation<DateRangeInput, Activity>(
		name = "stats.activity",
		description = "Words written per day over a date range, plus lifetime words per device.",
		access = Access.Read,
		agentVisible = true,
	) { input ->
		val logs = loadLogs(input)
		val timeZone = TimeZone.currentSystemDefault()
		val days = logs.flatMap { it.sessions }
			.filter { it.wordsWritten > 0 }
			.groupBy { it.startedAt.toLocalDateTime(timeZone).date }
			.filterKeys { input.contains(it) }
			.map { (date, sessions) -> DayWords(date, sessions.sumOf { it.wordsWritten }) }
			.sortedBy { it.date }
		val devices = logs.groupBy { it.deviceLabel }
			.map { (device, deviceLogs) ->
				DeviceWords(device, deviceLogs.sumOf { log -> log.sessions.sumOf { it.wordsWritten.coerceAtLeast(0) } })
			}
			.filter { it.words > 0 }
			.sortedByDescending { it.words }
		Activity(days, devices)
	},
	operation<DateRangeInput, Sessions>(
		name = "stats.sessions",
		description = "Writing sessions over a date range, from every device.",
		access = Access.Read,
		agentVisible = true,
	) { input ->
		val timeZone = TimeZone.currentSystemDefault()
		val sessions = loadLogs(input).flatMap { log ->
			log.sessions
				.filter { input.contains(it.startedAt.toLocalDateTime(timeZone).date) }
				.map { Session(log.deviceLabel, it.startedAt, it.endedAt, it.wordsWritten) }
		}
		Sessions(sessions.sortedBy { it.startedAt })
	},
)

private suspend fun OperationContext.loadLogs(input: DateRangeInput): Collection<DeviceLog> {
	if (input.from != null && input.to != null && input.from > input.to) invalidInput("from is after to")
	return projects.withProject(input.project) { project ->
		project.scope.get<WritingActivityRepository>().loadAllLogs().values
	}
}

@Serializable
data class StatsProjectInput(
	val project: String,
	/** Recalculate even when the cached statistics are current. */
	val recalculate: Boolean = false,
)

@Serializable
data class ProjectStats(
	val totalWords: Int,
	val sceneCount: Int,
	/** Top-level scenes and groups, in story order. */
	val chapters: List<ChapterWords>,
	val longestScene: SceneWords?,
	val shortestSceneWords: Int,
	val medianSceneWords: Int,
	val noteCount: Int,
	val timelineEventCount: Int,
	val entryCounts: List<EntryCount>,
	/** People appearing in the most scenes. */
	val topAppearances: List<Appearance>,
	val wordCountGoal: WordGoal?,
	val lastEdited: Instant?,
	val lastEditedScene: SceneRef?,
)

@Serializable
data class ChapterWords(val id: Int, val name: String, val words: Int)

@Serializable
data class SceneWords(val id: Int, val name: String, val words: Int)

@Serializable
data class SceneRef(val id: Int, val name: String)

@Serializable
data class EntryCount(val type: EntryKind, val count: Int)

@Serializable
data class Appearance(val entryId: Int, val name: String, val type: EntryKind, val sceneCount: Int)

@Serializable
data class DateRangeInput(
	val project: String,
	/** First day included, in the device's time zone; omitted for no lower bound. */
	val from: LocalDate? = null,
	/** Last day included; omitted for no upper bound. */
	val to: LocalDate? = null,
) {
	internal fun contains(date: LocalDate): Boolean =
		(from == null || date >= from) && (to == null || date <= to)
}

@Serializable
data class Activity(
	val days: List<DayWords>,
	/** Lifetime totals, not limited to the range. */
	val devices: List<DeviceWords>,
)

@Serializable
data class DayWords(val date: LocalDate, val words: Int)

@Serializable
data class DeviceWords(val device: String, val words: Int)

@Serializable
data class Sessions(val sessions: List<Session>)

@Serializable
data class Session(val device: String, val startedAt: Instant, val endedAt: Instant, val words: Int)
