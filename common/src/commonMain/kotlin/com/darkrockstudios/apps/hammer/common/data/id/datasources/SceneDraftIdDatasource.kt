package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneDraftIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findIds(projectDef: ProjectDef): Set<Int> {
		val draftsDir = SceneDraftsDatasource.getDraftsDirectory(projectDef, fileSystem).toOkioPath()
		return fileSystem.list(draftsDir)
			.filter { sceneDir -> sceneDir.name.toIntOrNull() != null }
			.flatMap { sceneDir -> fileSystem.list(sceneDir) }
			.mapNotNull { path -> SceneDraftsDatasource.parseDraftFileName(path.name) }
			.map { it.id }
			.toSet()
	}
}
