package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneDraftIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val draftsDir = SceneDraftsDatasource.getDraftsDirectory(projectDef, fileSystem).toOkioPath()
		val maxId: Int = fileSystem.list(draftsDir)
			.filter { sceneDir -> sceneDir.name.toIntOrNull() != null }
			.flatMap { sceneDir -> fileSystem.list(sceneDir) }
			.mapNotNull { path -> SceneDraftsDatasource.parseDraftFileName(path.name) }
			.maxOfOrNull { it.id } ?: -1
		return maxId
	}
}
