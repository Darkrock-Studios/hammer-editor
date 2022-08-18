package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.Tree
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class ProjectEditorRepository(
    val projectDef: ProjectDef,
    private val projectsRepository: ProjectsRepository
) {
    val rootScene = SceneItem(
        projectDef = projectDef,
        type = SceneItem.Type.Root,
        id = SceneItem.ROOT_ID,
        name = "",
        order = 0
    )

    private var nextSceneId: Int = 0

    private val editorScope = CoroutineScope(defaultDispatcher)
    private val contentChannel = Channel<SceneContent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _bufferUpdateChannel = MutableSharedFlow<SceneBuffer>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val bufferUpdateChannel: SharedFlow<SceneBuffer> = _bufferUpdateChannel

    private val _sceneListChannel = MutableSharedFlow<SceneSummary>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val sceneListChannel: SharedFlow<SceneSummary> = _sceneListChannel

    protected val sceneTree = Tree<SceneItem>()

    protected abstract fun loadSceneTree(): TreeNode<SceneItem>

    // Runs through the whole tree and makes the scene order match the tree order
    // this fixes changes that were made else where or possibly due to crashes
    private fun cleanupSceneOrder() {
        val groups = sceneTree.filter {
            it.value.type == SceneItem.Type.Group ||
                    it.value.type == SceneItem.Type.Root
        }

        groups.forEach { node ->
            updateSceneOrder(node.value.id)
        }
    }

    fun subscribeToBufferUpdates(
        sceneDef: SceneItem?,
        scope: CoroutineScope,
        onBufferUpdate: (SceneBuffer) -> Unit
    ): Job {
        return scope.launch {
            bufferUpdateChannel.collect { newBuffer ->
                if (sceneDef == null || newBuffer.content.scene.id == sceneDef.id) {
                    onBufferUpdate(newBuffer)
                }
            }
        }
    }

    fun subscribeToSceneUpdates(
        scope: CoroutineScope,
        onSceneListUpdate: (SceneSummary) -> Unit
    ): Job {
        val job = scope.launch {
            sceneListChannel.collect { scenes ->
                onSceneListUpdate(scenes)
            }
        }
        reloadScenes()
        return job
    }

    private val sceneBuffers = mutableMapOf<Int, SceneBuffer>()

    private val storeTempJobs = mutableMapOf<Int, Job>()
    private fun launchSaveJob(sceneDef: SceneItem) {
        val job = storeTempJobs[sceneDef.id]
        job?.cancel("Starting a new one")
        storeTempJobs[sceneDef.id] = editorScope.launch {
            delay(2.toDuration(DurationUnit.SECONDS))
            storeTempSceneBuffer(sceneDef)
            storeTempJobs.remove(sceneDef.id)
        }
    }

    protected fun getDirtyBufferIds(): Set<Int> = sceneBuffers
        .filter { it.value.dirty }
        .map { it.key }
        .toSet()

    protected fun cancelTempStoreJob(sceneDef: SceneItem) {
        storeTempJobs.remove(sceneDef.id)?.cancel("cancelTempStoreJob")
    }

    /**
     * This needs to be called after instantiation
     */
    fun initializeProjectEditor() {
        val root = loadSceneTree()
        sceneTree.setRoot(root)

        cleanupSceneOrder()

        val lastSceneId = findLastSceneId()
        if (lastSceneId != null) {
            setLastSceneId(lastSceneId)
        } else {
            setLastSceneId(0)
        }

        // Load any existing temp scenes into buffers
        val tempContent = getSceneTempBufferContents()
        for (content in tempContent) {
            val buffer = SceneBuffer(content, true)
            updateSceneBuffer(buffer)
        }

        reloadScenes()

        editorScope.launch {
            while (isActive) {
                val result = contentChannel.receiveCatching()
                if (result.isSuccess) {
                    val content = result.getOrNull()
                    if (content == null) {
                        Napier.w { "ProjectEditorRepository failed to get content from contentChannel" }
                    } else {
                        updateSceneBufferContent(content)
                        launchSaveJob(content.scene)
                    }
                }
            }
        }
    }

    /**
     * Returns null if there are no scenes yet
     */
    private fun findLastSceneId(): Int? = sceneTree.toList().maxByOrNull { it.value.id }?.value?.id

    private fun setLastSceneId(lastSceneId: Int) {
        nextSceneId = lastSceneId + 1
    }

    protected fun claimNextSceneId(): Int {
        val newSceneId = nextSceneId
        nextSceneId += 1
        return newSceneId
    }

    abstract fun getSceneFilename(path: HPath): String
    abstract fun getSceneParentPath(path: HPath): ScenePathSegments
    abstract fun getScenePathSegments(path: HPath): ScenePathSegments
    abstract fun getSceneFilePath(sceneId: Int): HPath
    abstract fun getSceneDirectory(): HPath
    abstract fun getSceneBufferDirectory(): HPath
    abstract fun getSceneFilePath(scene: SceneItem, isNewScene: Boolean = false): HPath
    abstract fun getSceneBufferTempPath(sceneDef: SceneItem): HPath
    abstract fun createScene(parent: SceneItem?, sceneName: String): SceneItem?
    abstract fun createGroup(parent: SceneItem?, groupName: String): SceneItem?
    abstract fun deleteScene(scene: SceneItem): Boolean
    abstract fun deleteGroup(scene: SceneItem): Boolean
    abstract fun getScenes(): List<SceneItem>
    abstract fun getSceneTree(): ImmutableTree<SceneItem>
    abstract fun getScenes(root: HPath): List<SceneItem>
    abstract fun getSceneTempBufferContents(): List<SceneContent>
    abstract fun getSceneAtIndex(index: Int): SceneItem
    abstract fun getSceneFromPath(path: HPath): SceneItem
    abstract fun loadSceneBuffer(sceneDef: SceneItem): SceneBuffer
    abstract fun storeSceneBuffer(sceneDef: SceneItem): Boolean
    abstract fun storeTempSceneBuffer(sceneDef: SceneItem): Boolean
    abstract fun clearTempScene(sceneDef: SceneItem)
    abstract fun getLastOrderNumber(parentId: Int?): Int
    abstract fun getLastOrderNumber(parentPath: HPath): Int
    abstract fun updateSceneOrder(parentId: Int)
    abstract fun moveScene(moveRequest: MoveRequest)
    abstract fun renameScene(sceneDef: SceneItem, newName: String)

    fun getSceneSummaries(): SceneSummary {
        return SceneSummary(
            getSceneTree(),
            getDirtyBufferIds()
        )
    }

    protected fun reloadScenes(summary: SceneSummary? = null) {
        val scenes = summary ?: getSceneSummaries()
        _sceneListChannel.tryEmit(scenes)
    }

    fun onContentChanged(content: SceneContent) {
        editorScope.launch {
            contentChannel.send(content)
        }
    }

    private fun updateSceneBufferContent(content: SceneContent) {
        val oldBuffer = sceneBuffers[content.scene.id]
        // Skip update if nothing is different
        if (content != oldBuffer?.content) {
            val newBuffer = SceneBuffer(content, true)
            updateSceneBuffer(newBuffer)
        }
    }

    protected fun updateSceneBuffer(newBuffer: SceneBuffer) {
        sceneBuffers[newBuffer.content.scene.id] = newBuffer
        _bufferUpdateChannel.tryEmit(newBuffer)
    }

    protected fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? = sceneBuffers[sceneDef.id]
    protected fun hasSceneBuffer(sceneDef: SceneItem): Boolean =
        sceneBuffers.containsKey(sceneDef.id)

    protected fun hasDirtyBuffer(sceneDef: SceneItem): Boolean =
        getSceneBuffer(sceneDef)?.dirty == true

    fun hasDirtyBuffers(): Boolean = sceneBuffers.any { it.value.dirty }

    fun storeAllBuffers() {
        val dirtyScenes = sceneBuffers.filter { it.value.dirty }.map { it.value.content.scene }
        dirtyScenes.forEach { scene ->
            storeSceneBuffer(scene)
        }
    }

    fun discardSceneBuffer(sceneDef: SceneItem) {
        if (hasSceneBuffer(sceneDef)) {
            sceneBuffers.remove(sceneDef.id)
            clearTempScene(sceneDef)
            loadSceneBuffer(sceneDef)
        }
    }

    private fun willNextSceneIncreaseMagnitude(parentId: Int?): Boolean {
        val lastOrder = getLastOrderNumber(parentId)
        return lastOrder.numDigits() < (lastOrder + 1).numDigits()
    }

    fun getSceneFileName(
        sceneDef: SceneItem,
        isNewScene: Boolean = false
    ): String {
        val parent = getSceneParentFromId(sceneDef.id)
        val parentId: Int = if (parent == null || parent.isRootScene) {
            rootScene.id
        } else {
            parent.id
        }
        val parentPath = getSceneFilePath(parentId)

        val orderDigits = if (isNewScene && willNextSceneIncreaseMagnitude(parentId)) {
            getLastOrderNumber(parentPath).numDigits() + 1
        } else {
            getLastOrderNumber(parentPath).numDigits()
        }

        val order = sceneDef.order.toString().padStart(orderDigits, '0')
        val bareName = "$order-${sceneDef.name}-${sceneDef.id}"

        val filename = if (sceneDef.type == SceneItem.Type.Scene) {
            "$bareName.md"
        } else {
            bareName
        }
        return filename
    }

    fun getSceneTempFileName(sceneDef: SceneItem): String {
        return "${sceneDef.id}.md"
    }

    fun getSceneIdFromBufferFilename(fileName: String): Int {
        val captures = SCENE_BUFFER_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad: $fileName")

        try {
            val sceneId = captures.groupValues[1].toInt()
            return sceneId
        } catch (e: NumberFormatException) {
            throw InvalidSceneBufferFilename("Number format exception", fileName)
        } catch (e: IllegalStateException) {
            throw InvalidSceneBufferFilename("Invalid filename", fileName)
        }
    }

    fun getSceneIdFromFilename(path: HPath): Int {
        val fileName = getSceneFilename(path)
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad: $fileName")
        try {
            val sceneId = captures.groupValues[3].toInt()
            return sceneId
        } catch (e: NumberFormatException) {
            throw InvalidSceneFilename("Number format exception", fileName)
        } catch (e: IllegalStateException) {
            throw InvalidSceneFilename("Invalid filename", fileName)
        }
    }

    @Throws(InvalidSceneFilename::class)
    fun getSceneFromFilename(path: HPath): SceneItem {
        val fileName = getSceneFilename(path)

        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad: $fileName")

        try {
            val sceneOrder = captures.groupValues[1].toInt()
            val sceneName = captures.groupValues[2]
            val sceneId = captures.groupValues[3].toInt()
            val isSceneGroup = !(captures.groupValues.size >= 5
                    && captures.groupValues[4] == SCENE_FILENAME_EXTENSION)

            val sceneItem = SceneItem(
                projectDef = projectDef,
                type = if (isSceneGroup) SceneItem.Type.Group else SceneItem.Type.Scene,
                id = sceneId,
                name = sceneName,
                order = sceneOrder,
            )

            return sceneItem
        } catch (e: NumberFormatException) {
            throw InvalidSceneFilename("Number format exception", fileName)
        } catch (e: IllegalStateException) {
            throw InvalidSceneFilename("Invalid filename", fileName)
        }
    }

    fun getSceneItemFromId(id: Int): SceneItem? {
        return sceneTree.findValueOrNull { it.id == id }
    }

    protected fun getSceneNodeFromId(id: Int): TreeNode<SceneItem>? {
        return sceneTree.findOrNull { it.id == id }
    }

    fun getSceneParentFromId(id: Int): SceneItem? {
        return sceneTree.findOrNull { it.id == id }?.parent?.value
    }

    fun validateSceneName(sceneName: String) = projectsRepository.validateFileName(sceneName)

    fun close() {
        contentChannel.close()
        runBlocking {
            storeTempJobs.forEach { it.value.join() }
        }
        editorScope.cancel("Editor Closed")
        // During a proper shutdown, we clear any remaining temp buffers that haven't been saved yet
        getSceneTempBufferContents().forEach {
            clearTempScene(it.scene)
        }
    }

    companion object {
        val SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\da-zA-Z _']+)-(\d+)(\.md)?(?:\.temp)?""")
        val SCENE_BUFFER_FILENAME_PATTERN = Regex("""(\d+)\.md""")
        const val SCENE_FILENAME_EXTENSION = ".md"
        const val SCENE_DIRECTORY = "scenes"
        const val BUFFER_DIRECTORY = ".buffers"
        const val tempSuffix = ".temp"
    }

    abstract fun getHpath(sceneItem: SceneItem): HPath
}

open class InvalidSceneFilename(message: String, fileName: String) :
    IllegalStateException("$fileName failed to parse because: $message")

class InvalidSceneBufferFilename(message: String, fileName: String) :
    InvalidSceneFilename(message, fileName)