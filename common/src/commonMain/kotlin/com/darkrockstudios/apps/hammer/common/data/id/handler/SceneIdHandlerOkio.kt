package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.filterScenePathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneIdHandlerOkio(
	private val fileSystem: FileSystem
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val sceneDir = SceneEditorRepositoryOkio.getSceneDirectory(projectDef, fileSystem).toOkioPath()

		val maxId: Int = fileSystem.listRecursively(sceneDir)
			.filterScenePathsOkio().maxOfOrNull { path ->
				SceneEditorRepository.getSceneIdFromFilename(path.name)
			} ?: -1

		return maxId
	}
}