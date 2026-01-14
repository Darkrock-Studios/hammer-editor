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

		// Get max from regular scenes (filterScenePathsOkio excludes archived)
		val regularMaxId: Int = fileSystem.listRecursively(sceneDir)
			.filterScenePathsOkio().maxOfOrNull { path ->
				SceneDatasource.getSceneIdFromFilename(path.name)
			} ?: -1

		// Also check archived scenes (supports both old and new filename formats)
		val archivedDir = SceneDatasource.getArchivedDirectory(projectDef, fileSystem).toOkioPath()
		val archivedMaxId: Int = if (fileSystem.exists(archivedDir)) {
			fileSystem.list(archivedDir)
				.filter { SceneDatasource.validateArchivedSceneFilename(it.name) }
				.maxOfOrNull { SceneDatasource.getSceneIdFromFilename(it.name) } ?: -1
		} else {
			-1
		}

		return maxOf(regularMaxId, archivedMaxId)
	}
}