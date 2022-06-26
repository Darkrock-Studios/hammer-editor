package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.IOException

class ProjectEditorRepositoryOkio(
    projectDef: ProjectDef,
    projectsRepository: ProjectsRepository,
    private val fileSystem: FileSystem
) : ProjectEditorRepository(projectDef, projectsRepository) {

    override fun getSceneDirectory(): HPath {
        val projOkPath = projectDef.path.toOkioPath()
        val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
        fileSystem.createDirectory(sceneDirPath)
        return sceneDirPath.toHPath()
    }

    override fun getScenePath(sceneDef: SceneDef, isNewScene: Boolean): HPath {
        val scenePathSegment = getSceneDirectory().toOkioPath()
        val fileName = getSceneFileName(sceneDef, isNewScene)
        return scenePathSegment.div(fileName).toHPath()
    }

    override fun getSceneFromPath(path: HPath): SceneDef {
        val okPath = path.toOkioPath()
        val sceneDef = getSceneDefFromFilename(okPath.name)
        return sceneDef
    }

    override fun updateSceneOrder() {
        val sceneDirPath = getSceneDirectory().toOkioPath()
        val scenePaths = fileSystem.list(sceneDirPath).sortedBy { it.name }
        scenePaths.forEachIndexed { index, path ->
            val scene = getSceneFromPath(path.toHPath())
            val newOrderScene = scene.copy(order = index + 1)
            val newPath = getScenePath(newOrderScene).toOkioPath()
            fileSystem.atomicMove(path, newPath)
        }
    }

    override fun moveScene(from: Int, to: Int) {
        val sceneDirPath = getSceneDirectory().toOkioPath()
        val scenePaths = fileSystem.list(sceneDirPath).sortedBy { it.name }.toMutableList()

        val target = scenePaths.removeAt(from)
        scenePaths.add(to, target)

        val tempPaths = scenePaths.map { path ->
            val tempName = path.name + tempSuffix
            val tempPath = path.parent!!.div(tempName)

            fileSystem.atomicMove(source = path, target = tempPath)

            tempPath
        }

        scenePaths.forEachIndexed { index, path ->
            val originalScene = getSceneDefFromFilename(path.name)
            val newScene = originalScene.copy(order = index + 1)

            val tempPath = tempPaths[index]
            val targetPath = getScenePath(newScene).toOkioPath()

            fileSystem.atomicMove(source = tempPath, target = targetPath)
        }
    }

    override fun createScene(sceneName: String): SceneDef? {
        Napier.d("createScene: $sceneName")
        return if (!validateSceneName(sceneName)) {
            Napier.d("Invalid scene name")
            null
        } else {
            val lastOrder = getLastOrderNumber()
            val nextOrder = lastOrder + 1
            val sceneId = claimNextSceneId()

            val newSceneDef = SceneDef(
                projectDef = projectDef,
                id = sceneId,
                name = sceneName,
                order = nextOrder,
            )

            val scenePath = getScenePath(newSceneDef, true).toOkioPath()
            fileSystem.write(scenePath, true) {
                writeUtf8("")
            }

            if (lastOrder.numDigits() < nextOrder.numDigits()) {
                updateSceneOrder()
            }

            newSceneDef
        }
    }

    override fun deleteScene(sceneDef: SceneDef): Boolean {
        val scenePath = getScenePath(sceneDef).toOkioPath()
        return if (fileSystem.exists(scenePath)) {
            fileSystem.delete(scenePath)
            updateSceneOrder()

            true
        } else {
            false
        }
    }

    override fun getScenes(): List<SceneDef> {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        return fileSystem.list(sceneDirectory)
            .filter { fileSystem.metadata(it).isRegularFile }
            .map { path ->
                getSceneDefFromFilename(path.name)
            }
    }

    override fun getSceneAtIndex(index: Int): SceneDef {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        val scenePaths = fileSystem.list(sceneDirectory)
        if (index >= scenePaths.size) throw IllegalArgumentException("Invalid scene index requested: $index")
        val scenePath = fileSystem.list(sceneDirectory)[index]
        return getSceneFromPath(scenePath.toHPath())
    }

    override fun loadSceneContent(sceneDef: SceneDef): String? {
        val scenePath = getScenePath(sceneDef).toOkioPath()
        return try {
            val content = fileSystem.read(scenePath) {
                readUtf8()
            }
            content
        } catch (e: IOException) {
            Napier.e("Failed to load Scene (${sceneDef.name})")
            null
        }
    }

    override fun storeSceneContent(newContent: SceneContent): Boolean {
        val scenePath = getScenePath(newContent.sceneDef).toOkioPath()
        return try {
            fileSystem.write(scenePath) {
                writeUtf8(newContent.content)
            }
            true
        } catch (e: IOException) {
            Napier.e("Failed to load Scene (${newContent.sceneDef.name})")
            false
        }
    }

    override fun getLastOrderNumber(): Int {
        val scenePath = getSceneDirectory().toOkioPath()
        val numScenes = fileSystem.list(scenePath).filter {
            fileSystem.metadataOrNull(it)?.isRegularFile == true
        }.count()
        return numScenes
    }
}