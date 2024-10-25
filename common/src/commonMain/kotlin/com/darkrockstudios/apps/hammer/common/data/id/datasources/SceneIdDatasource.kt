package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.filterScenePathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val sceneDir = SceneDatasource.getSceneDirectory(projectDef, fileSystem).toOkioPath()

		val maxId: Int = fileSystem.listRecursively(sceneDir)
			.filterScenePathsOkio().maxOfOrNull { path ->
				SceneDatasource.getSceneIdFromFilename(path.name)
			} ?: -1

		return maxId
	}
}