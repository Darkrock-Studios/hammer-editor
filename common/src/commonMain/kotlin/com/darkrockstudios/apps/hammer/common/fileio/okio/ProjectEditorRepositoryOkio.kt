package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.IOException

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
        } else if (!projectsRepository.validateFileName(sceneName)) {
            Napier.d("Invalid scene name")
            null
        } else {
            fileSystem.write(scenePath, true) {
                writeUtf8("")
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

    override fun loadSceneContent(scene: Scene): String? {
        val scenePath = getScenePath(scene.name).toOkioPath()
        return try {
            val content = fileSystem.read(scenePath) {
                readUtf8()
            }
            content
        } catch (e: IOException) {
            Napier.e("Failed to load Scene (${scene.name})")
            null
        }
    }

    override fun storeSceneContent(newContent: SceneContent): Boolean {
        val scenePath = getScenePath(newContent.scene.name).toOkioPath()
        return try {
            fileSystem.write(scenePath) {
                writeUtf8(newContent.content)
            }
            true
        } catch (e: IOException) {
            Napier.e("Failed to load Scene (${newContent.scene.name})")
            false
        }
    }
}