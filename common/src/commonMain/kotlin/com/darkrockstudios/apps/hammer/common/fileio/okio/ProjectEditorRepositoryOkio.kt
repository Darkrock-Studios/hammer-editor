package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.Scene
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import okio.FileSystem

class ProjectEditorRepositoryOkio(
    project: Project,
    projectsRepository: ProjectsRepository,
    private val fileSystem: FileSystem
) : ProjectEditorRepository(project, projectsRepository) {

    override fun getSceneDirectory(): HPath {
        val projOkPath = project.path.toOkioPath()
        val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
        fileSystem.createDirectory(sceneDirPath)
        return sceneDirPath.toHPath()
    }

    override fun getScenePath(sceneName: String): HPath {
        val scenePathSegment = getSceneDirectory().toOkioPath()
        val fileName = getSceneFileName(sceneName)
        return scenePathSegment.div(fileName).toHPath()
    }

    override fun createScene(sceneName: String): Scene? {
        val scenePath = getScenePath(sceneName).toOkioPath()
        Napier.d("createScene: $scenePath")
        return if (fileSystem.exists(scenePath)) {
            Napier.d("scene already existed")
            null
        } else if (projectsRepository.validateFileName(sceneName)) {
            Napier.d("Invalid scene name")
            null
        } else {
            fileSystem.write(scenePath, true) {
                writeUtf8("\n")
            }

            Scene(project, sceneName)
        }
    }

    override fun getScenes(): List<Scene> {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        return fileSystem.list(sceneDirectory)
            .filter { fileSystem.metadata(it).isRegularFile }
            .map { path -> Scene(project, getSceneNameFromFileName(path.name)) }
    }
}