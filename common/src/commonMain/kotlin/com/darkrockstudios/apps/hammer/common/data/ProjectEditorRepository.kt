package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.fileio.HPath

abstract class ProjectEditorRepository(
    val project: Project,
    protected val projectsRepository: ProjectsRepository
) {
    abstract fun getSceneDirectory(): HPath
    abstract fun getScenePath(sceneName: String): HPath
    abstract fun createScene(sceneName: String): Scene?
    abstract fun getScenes(): List<Scene>

    fun getSceneFileName(sceneName: String): String {
        return "$sceneName.txt"
    }

    fun getSceneNameFromFileName(fileName: String): String {
        return fileName.substringBefore(".")
    }

    companion object {
        const val SCENE_DIRECTORY = "scenes"
    }
}