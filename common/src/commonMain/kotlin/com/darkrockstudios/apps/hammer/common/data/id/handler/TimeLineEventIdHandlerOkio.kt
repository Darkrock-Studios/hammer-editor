package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

class TimeLineEventIdHandlerOkio(
	private val fileSystem: FileSystem,
	private val toml: Toml
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val filePath = TimeLineDatasource.getTimelineFilePath(projectDef)
		val timeline = TimeLineDatasource.loadTimeline(filePath, fileSystem, toml)
		val maxId = timeline.events.maxOfOrNull { it.id }
		return maxId ?: -1
	}
}