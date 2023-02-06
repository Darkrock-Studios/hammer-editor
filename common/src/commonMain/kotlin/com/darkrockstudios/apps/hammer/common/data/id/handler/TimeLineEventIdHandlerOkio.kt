package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import kotlinx.serialization.json.Json
import okio.FileSystem

class TimeLineEventIdHandlerOkio(
	private val fileSystem: FileSystem,
	//private val toml: Toml
	private val json: Json
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val filePath = TimeLineRepositoryOkio.getTimelineFile(projectDef)
		val timeline = TimeLineRepositoryOkio.loadTimeline(filePath, fileSystem, json)
		val maxId = timeline.events.maxOfOrNull { it.id }
		return maxId ?: -1
	}
}