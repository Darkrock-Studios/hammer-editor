package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.IOException
import okio.Path

class ProjectEditorRepositoryOkio(
    projectDef: ProjectDef,
    projectsRepository: ProjectsRepository,
    private val fileSystem: FileSystem
) : ProjectEditorRepository(projectDef, projectsRepository) {

    override fun getSceneFilename(path: HPath) = path.toOkioPath().name

    override fun getSceneParentPath(path: HPath): ScenePath {
        val parentPath = path.toOkioPath().parent
        return if (parentPath != null && parentPath.name != "..") {
            getScenePath(parentPath.toHPath())
        } else {
            ScenePath(emptyList())
        }
    }

    override fun getScenePath(path: HPath): ScenePath {
        val parentPath = path.toOkioPath()

        val sceneDir = getSceneDirectory().toOkioPath()
        return if (parentPath != sceneDir) {
            val relativePath: Path = parentPath.relativeTo(sceneDir)

            val parentScenes = relativePath.segments.mapIndexed { ii, segment ->

                var pathBuilder = sceneDir
                relativePath.segments.subList(0, ii).forEach { name ->
                    pathBuilder = pathBuilder.div(name)
                }
                pathBuilder = pathBuilder.div(segment)

                getSceneFromFilename(pathBuilder.toHPath(), false)
            }

            ScenePath(pathSegments = parentScenes)
        } else {
            ScenePath(pathSegments = emptyList())
        }
    }

    override fun getHpath(sceneItem: SceneItem): HPath {
        val sceneDir = getSceneDirectory().toOkioPath()

        var path = sceneDir
        sceneItem.parentPath.pathSegments.forEach { item ->
            val itemPath = getScenePath(item)
            val filename = getSceneFilename(itemPath)
            path = path.div(filename)
        }

        return path.toHPath()
    }

    override fun getSceneDirectory(): HPath {
        val projOkPath = projectDef.path.toOkioPath()
        val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
        fileSystem.createDirectory(sceneDirPath)
        return sceneDirPath.toHPath()
    }

    override fun getSceneBufferDirectory(): HPath {
        val projOkPath = projectDef.path.toOkioPath()
        val sceneDirPath = projOkPath.div(SCENE_DIRECTORY)
        val bufferPathSegment = sceneDirPath.div(BUFFER_DIRECTORY)
        fileSystem.createDirectory(bufferPathSegment)
        return bufferPathSegment.toHPath()
    }

    override fun getScenePath(sceneDef: SceneItem, isNewScene: Boolean): HPath {
        val scenePathSegment = getSceneDirectory().toOkioPath()
        val fileName = getSceneFileName(sceneDef, isNewScene)

        var fullPath: Path = scenePathSegment
        sceneDef.parentPath.pathSegments.forEach { segment ->
            val segmentName = getSceneFileName(segment)
            fullPath = fullPath.div(segmentName)
        }

        return fullPath.div(fileName).toHPath()
    }

    override fun getSceneBufferTempPath(sceneDef: SceneItem): HPath {
        val bufferPathSegment = getSceneBufferDirectory().toOkioPath()
        val fileName = getSceneTempFileName(sceneDef)
        return bufferPathSegment.div(fileName).toHPath()
    }

    override fun getSceneFromPath(path: HPath): SceneItem {
        val sceneDef = getSceneFromFilename(path)
        return sceneDef
    }

    private fun getAllScenePathsOkio(): List<Path> {
        val sceneDirPath = getSceneDirectory().toOkioPath()
        val scenePaths = fileSystem.listRecursively(sceneDirPath)
            .filter { it.name != BUFFER_DIRECTORY }
            .filter { !it.name.endsWith(tempSuffix) }
            .toList()
            .sortedBy { it.name }
        return scenePaths
    }

    private fun getScenePathsOkio(root: Path): List<Path> {
        val scenePaths = fileSystem.list(root)
            .filter { it.name != BUFFER_DIRECTORY }
            .filter { !it.name.endsWith(tempSuffix) }
            .sortedBy { it.name }
        return scenePaths
    }

    override fun updateSceneOrder() {
        val scenePaths = getAllScenePathsOkio()
        scenePaths.forEachIndexed { index, path ->
            val scene = getSceneFromPath(path.toHPath())
            val newOrderScene = scene.copy(order = index + 1)
            val newPath = getScenePath(newOrderScene).toOkioPath()
            fileSystem.atomicMove(path, newPath)
        }

        reloadScenes()
    }

    override fun moveScene(from: Int, to: Int) {
        val scenePaths = getAllScenePathsOkio().toMutableList()

        val target = scenePaths.removeAt(from)
        scenePaths.add(to, target)

        val tempPaths = scenePaths.map { path ->
            val tempName = path.name + tempSuffix
            val tempPath = path.parent!!.div(tempName)

            fileSystem.atomicMove(source = path, target = tempPath)

            tempPath
        }

        scenePaths.forEachIndexed { index, path ->
            val originalScene = getSceneFromFilename(path.toHPath())
            val newScene = originalScene.copy(order = index + 1)

            val tempPath = tempPaths[index]
            val targetPath = getScenePath(newScene).toOkioPath()

            fileSystem.atomicMove(source = tempPath, target = targetPath)
        }

        reloadScenes()
    }

    override fun createScene(parent: SceneItem?, sceneName: String): SceneItem? {
        val cleanedNamed = sceneName.trim()

        Napier.d("createScene: $cleanedNamed")
        return if (!validateSceneName(cleanedNamed)) {
            Napier.d("Invalid scene name")
            null
        } else {
            val parentPath = if (parent != null) {
                val parentPath = getScenePath(parent)
                getScenePath(parentPath)
            } else {
                ScenePath(pathSegments = emptyList())
            }

            val lastOrder = getLastOrderNumber()
            val nextOrder = lastOrder + 1
            val sceneId = claimNextSceneId()

            val newSceneItem = SceneItem(
                projectDef = projectDef,
                type = SceneItem.Type.Scene,
                id = sceneId,
                name = cleanedNamed,
                order = nextOrder,
                parentPath = parentPath
            )

            val scenePath = getScenePath(newSceneItem, true).toOkioPath()
            fileSystem.write(scenePath, true) {
                writeUtf8("")
            }

            if (lastOrder.numDigits() < nextOrder.numDigits()) {
                updateSceneOrder()
            } else {
                reloadScenes()
            }

            newSceneItem
        }
    }

    override fun deleteScene(sceneDef: SceneItem): Boolean {
        val scenePath = getScenePath(sceneDef).toOkioPath()
        return if (fileSystem.exists(scenePath)) {
            fileSystem.delete(scenePath)
            updateSceneOrder()

            true
        } else {
            false
        }
    }

    override fun getScenes(): List<SceneItem> {
        return getAllScenePathsOkio()
            .filter { it.name != BUFFER_DIRECTORY }
            .filter { !it.name.endsWith(tempSuffix) && it.name != ".." }
            .map { path ->
                getSceneFromFilename(path.toHPath())
            }
    }

    override fun getRootScenes(): List<SceneItem> {
        return getScenes(getSceneDirectory())
    }

    override fun getScenes(root: HPath): List<SceneItem> {
        val rootOkia = root.toOkioPath()
        return getScenePathsOkio(rootOkia)
            .filter { it.name != BUFFER_DIRECTORY }
            .filter { !it.name.endsWith(tempSuffix) && it.name != ".." }
            .map { path ->
                getSceneFromFilename(path.toHPath())
            }
    }

    override fun getSceneTempBufferContents(): List<SceneContent> {
        val bufferDirectory = getSceneBufferDirectory().toOkioPath()
        return fileSystem.list(bufferDirectory)
            .filter { fileSystem.metadata(it).isRegularFile }
            .mapNotNull { path ->
                val id = getSceneIdFromBufferFilename(path.name)
                getSceneItemFromId(id)
            }
            .map { sceneDef ->
                val tempPath = getSceneBufferTempPath(sceneDef).toOkioPath()
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

    override fun getSceneAtIndex(index: Int): SceneItem {
        val scenePaths = getAllScenePathsOkio()
        if (index >= scenePaths.size) throw IllegalArgumentException("Invalid scene index requested: $index")
        val scenePath = scenePaths[index]
        return getSceneFromPath(scenePath.toHPath())
    }

    override fun loadSceneBuffer(sceneDef: SceneItem): SceneBuffer {
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

    override fun storeSceneBuffer(sceneDef: SceneItem): Boolean {
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

    override fun storeTempSceneBuffer(sceneDef: SceneItem): Boolean {
        val buffer = getSceneBuffer(sceneDef)
        if (buffer == null) {
            Napier.e { "Failed to store scene: ${sceneDef.id} - ${sceneDef.name}, no buffer present" }
            return false
        }

        val scenePath = getSceneBufferTempPath(sceneDef).toOkioPath()

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

    override fun clearTempScene(sceneDef: SceneItem) {
        val path = getSceneBufferTempPath(sceneDef).toOkioPath()
        fileSystem.delete(path)
    }

    override fun getLastOrderNumber(): Int {
        val scenePath = getSceneDirectory().toOkioPath()
        val numScenes = fileSystem.list(scenePath).count {
            fileSystem.metadataOrNull(it)?.isRegularFile == true
        }
        return numScenes
    }

    override fun getSceneItemFromId(id: Int): SceneItem? {
        return getScenes().find { it.id == id }
    }

    override fun renameScene(sceneDef: SceneItem, newName: String) {
        val cleanedNamed = newName.trim()

        val oldPath = getScenePath(sceneDef).toOkioPath()
        val newDef = sceneDef.copy(name = cleanedNamed)
        val newPath = getScenePath(newDef).toOkioPath()

        fileSystem.atomicMove(oldPath, newPath)

        reloadScenes()
    }
}