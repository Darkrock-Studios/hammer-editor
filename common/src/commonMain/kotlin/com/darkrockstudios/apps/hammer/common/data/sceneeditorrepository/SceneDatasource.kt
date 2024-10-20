package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository.Companion.SCENE_DIRECTORY
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class SceneDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
) {

	fun getSceneDirectory(): HPath = getSceneDirectory(projectDef, fileSystem)

	companion object {
		fun getSceneDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val projOkPath = projectDef.path.toOkioPath()
			val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
			if (!fileSystem.exists(sceneDirPath)) {
				fileSystem.createDirectories(sceneDirPath)
			}
			return sceneDirPath.toHPath()
		}
	}
}