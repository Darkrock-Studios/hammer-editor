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

    override fun getScenePath(scene: Scene): HPath {
        val scenePathSegment = getSceneDirectory().toOkioPath()
        val fileName = getSceneFileName(scene)
        return scenePathSegment.div(fileName).toHPath()
    }

    override fun createScene(sceneName: String): Scene? {
        Napier.d("createScene: $sceneName")
        return if (!projectsRepository.validateSceneName(sceneName)) {
            Napier.d("Invalid scene name")
            null
        } else {
            val order = getNextOrderNumber()
            val newScene = Scene(project, order, sceneName)
            val scenePath = getScenePath(newScene).toOkioPath()
            fileSystem.write(scenePath, true) {
                writeUtf8("")
            }

            newScene
        }
    }

    override fun getScenes(): List<Scene> {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        return fileSystem.list(sceneDirectory)
            .filter { fileSystem.metadata(it).isRegularFile }
            .map { path ->
                val order = getSceneOrderNumber(path.name)
                val fileName = getSceneNameFromFileName(path.name)
                Scene(project, order, fileName)
            }
    }

    override fun loadSceneContent(scene: Scene): String? {
        val scenePath = getScenePath(scene).toOkioPath()
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
        val scenePath = getScenePath(newContent.scene).toOkioPath()
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

    override fun getNextOrderNumber(): Int {
        val scenePath = getSceneDirectory().toOkioPath()
        val lastScene = fileSystem.list(scenePath).filter {
            fileSystem.metadataOrNull(it)?.isRegularFile == true
        }.sorted().lastOrNull()

        val lastOrder = if (lastScene == null) {
            0
        } else {
            getSceneOrderNumber(lastScene.name)
        }
        return lastOrder + 1
    }
}