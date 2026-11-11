package com.darkrockstudios.apps.hammer.common.data.migrator

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

/**
 * Renames each timeline event's `date` key to `label`. The wire field and the hash already call it
 * the label, so no event's hash changes and the sync journal is left alone.
 */
class Migration5_6(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : Migration {
	override val toVersion: Int = 6

	// Runs during startup, where a throw would fail every launch.
	@Suppress("TooGenericExceptionCaught")
	override fun migrate(projectDef: ProjectDef) {
		Logger.i("Begin Migration5_6 for '${projectDef.name}'...")
		try {
			renameDateToLabel(projectDef)
		} catch (e: Exception) {
			Logger.e("Migration5_6 failed to migrate the timeline of '${projectDef.name}', leaving it as is", e)
		}
		Logger.i("Migration5_6 for '${projectDef.name}' complete.")
	}

	private fun renameDateToLabel(projectDef: ProjectDef) {
		val hpath = TimeLineDatasource.getTimelineFilePath(projectDef)
		val path = hpath.toOkioPath()
		if (!fileSystem.exists(path)) return

		val text = fileSystem.read(path) { readUtf8() }
		if (text.isBlank()) return

		val events = toml.decodeFromString(UnionTimeline.serializer(), text).events
		if (events.none { it.date != null }) return

		val container = TimeLineContainer(events.map { it.toTimeLineEvent() })
		TimeLineDatasource.storeTimeline(hpath, fileSystem, toml, container)
	}
}

/** Decodes both the legacy `date` key and the current `label` key. */
@Serializable
private data class UnionTimeline(val events: List<UnionTimelineEvent> = emptyList())

@Serializable
private data class UnionTimelineEvent(
	val id: Int,
	val order: Int,
	val kind: TimelineKind = TimelineKind.EVENT,
	val subject: Int? = null,
	val title: String? = null,
	val date: String? = null,
	val label: String? = null,
	val content: String = "",
	val tags: Set<String> = emptySet(),
	val references: Set<Int> = emptySet(),
	val lane: String? = null,
	val milestone: Boolean = false,
	val start: StoryDate? = null,
	val end: StoryDate? = null,
	val cachedTitle: String? = null,
) {
	fun toTimeLineEvent() = TimeLineEvent(
		id = id,
		order = order,
		kind = kind,
		subject = subject,
		title = title,
		label = label ?: date,
		content = content,
		tags = tags,
		references = references,
		lane = lane,
		milestone = milestone,
		start = start,
		end = end,
		cachedTitle = cachedTitle,
	)
}
