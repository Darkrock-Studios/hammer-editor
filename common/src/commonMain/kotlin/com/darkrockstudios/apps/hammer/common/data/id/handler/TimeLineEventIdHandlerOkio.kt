package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import okio.FileSystem

class TimeLineEventIdHandlerOkio(
	private val fileSystem: FileSystem,
	private val toml: Toml
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val timeline = TimeLineRepositoryOkio.loadTimeline(projectDef, fileSystem, toml)
		val maxId = timeline.events.maxOfOrNull { it.id }
		return maxId ?: -1
	}
}