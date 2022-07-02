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

    override fun getSceneTempPath(sceneDef: SceneDef): HPath {
        val scenePathSegment = getSceneDirectory().toOkioPath()
        val fileName = getSceneTempFileName(sceneDef)
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
            .filter { !it.name.endsWith(tempSuffix) }
            .map { path ->
                getSceneDefFromFilename(path.name)
            }
    }

    override fun getTempSceneContents(): List<SceneContent> {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        return fileSystem.list(sceneDirectory)
            .filter { fileSystem.metadata(it).isRegularFile }
            .filter { it.name.endsWith(tempSuffix) }
            .map { path ->
                getSceneDefFromFilename(path.name)
            }
            .map { sceneDef ->
                val tempPath = getSceneTempPath(sceneDef).toOkioPath()
                val content = try {
                    fileSystem.read(tempPath) {
                        readUtf8()
                    }
                } catch (e: IOException) {
                    Napier.e("Failed to load Scene (${sceneDef.name})")
                    ""
                }
                SceneContent(sceneDef, content)
            }
    }

    override fun getSceneSummaries(): List<SceneSummary> {
        return getScenes().map { SceneSummary(it, hasDirtyBuffer(it)) }
    }

    override fun getSceneAtIndex(index: Int): SceneDef {
        val sceneDirectory = getSceneDirectory().toOkioPath()
        val scenePaths = fileSystem.list(sceneDirectory)
        if (index >= scenePaths.size) throw IllegalArgumentException("Invalid scene index requested: $index")
        val scenePath = fileSystem.list(sceneDirectory)[index]
        return getSceneFromPath(scenePath.toHPath())
    }

    override fun loadSceneBuffer(sceneDef: SceneDef): SceneBuffer {
        val scenePath = getScenePath(sceneDef).toOkioPath()

        return if (hasSceneBuffer(sceneDef)) {
            getSceneBuffer(sceneDef)
                ?: throw IllegalStateException("sceneBuffers did not contain buffer for scene: ${sceneDef.id} - ${sceneDef.name}")
        } else {
            val content = try {
                fileSystem.read(scenePath) {
                    readUtf8()
                }
            } catch (e: IOException) {
                Napier.e("Failed to load Scene (${sceneDef.name})")
                ""
            }

            val newBuffer = SceneBuffer(
                SceneContent(sceneDef, content)
            )

            updateSceneBuffer(newBuffer)

            newBuffer
        }
    }

    override fun storeSceneBuffer(sceneDef: SceneDef): Boolean {
        val buffer = getSceneBuffer(sceneDef)
        if (buffer == null) {
            Napier.e { "Failed to store scene: ${sceneDef.id} - ${sceneDef.name}, no buffer present" }
            return false
        }

        val scenePath = getScenePath(sceneDef).toOkioPath()

        return try {
            val markdown = buffer.content.coerceMarkdown()

            fileSystem.write(scenePath) {
                writeUtf8(markdown)
            }

            val cleanBuffer = buffer.copy(dirty = false)
            updateSceneBuffer(cleanBuffer)

            cancelTempStoreJob(sceneDef)
            clearTempScene(sceneDef)

            true
        } catch (e: IOException) {
            Napier.e("Failed to store scene: (${sceneDef.name}) with error: ${e.message}")
            false
        }
    }

    override fun storeTempSceneBuffer(sceneDef: SceneDef): Boolean {
        val buffer = getSceneBuffer(sceneDef)
        if (buffer == null) {
            Napier.e { "Failed to store scene: ${sceneDef.id} - ${sceneDef.name}, no buffer present" }
            return false
        }

        val scenePath = getSceneTempPath(sceneDef).toOkioPath()

        return try {
            val markdown = buffer.content.coerceMarkdown()

            fileSystem.write(scenePath) {
                writeUtf8(markdown)
            }

            Napier.e("Stored temp scene: (${sceneDef.name})")

            true
        } catch (e: IOException) {
            Napier.e("Failed to store temp scene: (${sceneDef.name}) with error: ${e.message}")
            false
        }
    }

    override fun clearTempScene(sceneDef: SceneDef) {
        val path = getSceneTempPath(sceneDef).toOkioPath()
        fileSystem.delete(path)
    }

    override fun getLastOrderNumber(): Int {
        val scenePath = getSceneDirectory().toOkioPath()
        val numScenes = fileSystem.list(scenePath).count {
            fileSystem.metadataOrNull(it)?.isRegularFile == true
        }
        return numScenes
    }
}