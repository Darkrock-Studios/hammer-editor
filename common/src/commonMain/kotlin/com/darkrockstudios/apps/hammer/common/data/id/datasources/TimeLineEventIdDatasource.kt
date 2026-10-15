package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

class TimeLineEventIdDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml
) : IdDatasource {
	override fun findIds(projectDef: ProjectDef): Set<Int>? {
		val filePath = TimeLineDatasource.getTimelineFilePath(projectDef)
		val timeline = TimeLineDatasource.loadTimelineOrNull(filePath, fileSystem, toml)
		return timeline?.events?.map { it.id }?.toSet()
	}
}