package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.filterScenePathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findIds(projectDef: ProjectDef): Set<Int> {
		val sceneDir = SceneDatasource.getSceneDirectory(projectDef, fileSystem).toOkioPath()

		// filterScenePathsOkio excludes archived
		val regularIds = fileSystem.listRecursively(sceneDir)
			.filterScenePathsOkio()
			.map { path -> SceneDatasource.getSceneIdFromFilename(path.name) }
			.toSet()

		// Archived scenes support both old and new filename formats
		val archivedDir = SceneDatasource.getArchivedDirectory(projectDef, fileSystem).toOkioPath()
		val archivedIds = if (fileSystem.exists(archivedDir)) {
			fileSystem.list(archivedDir)
				.filter { SceneDatasource.validateArchivedSceneFilename(it.name) }
				.map { SceneDatasource.getSceneIdFromFilename(it.name) }
				.toSet()
		} else {
			emptySet()
		}

		return regularIds + archivedIds
	}
}