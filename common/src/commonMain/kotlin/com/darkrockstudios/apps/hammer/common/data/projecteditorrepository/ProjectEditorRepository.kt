package com.darkrockstudios.apps.hammer.common.data.projecteditorrepository

import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.Tree
import com.darkrockstudios.apps.hammer.common.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescent
import com.darkrockstudios.apps.hammer.common.util.numDigits
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.Closeable
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.milliseconds

abstract class ProjectEditorRepository(
    val projectDef: ProjectDef,
    private val projectsRepository: ProjectsRepository,
    protected val idRepository: IdRepository
) : Closeable, KoinComponent {

    val rootScene = SceneItem(
        projectDef = projectDef,
        type = SceneItem.Type.Root,
        id = SceneItem.ROOT_ID,
        name = "",
        order = 0
    )

    private lateinit var metadata: ProjectMetadata

    protected val dispatcherMain by injectMainDispatcher()
    protected val dispatcherDefault by injectDefaultDispatcher()
    private val editorScope = CoroutineScope(dispatcherDefault)

    private val _contentFlow = MutableSharedFlow<SceneContent>(
        extraBufferCapacity = 1
    )
    private val contentFlow: SharedFlow<SceneContent> = _contentFlow
    private var contentUpdateJob: Job? = null

    private val _bufferUpdateFlow = MutableSharedFlow<SceneBuffer>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val bufferUpdateFlow: SharedFlow<SceneBuffer> = _bufferUpdateFlow

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
            bufferUpdateFlow.collect { newBuffer ->
                if (sceneDef == null || newBuffer.content.scene.id == sceneDef.id) {
                    withContext(dispatcherMain) {
                        onBufferUpdate(newBuffer)
                    }
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
                withContext(dispatcherMain) {
                    onSceneListUpdate(scenes)
                }
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
            storeTempSceneBuffer(sceneDef)
            storeTempJobs.remove(sceneDef.id)
        }
    }

    protected fun getDirtyBufferIds(): Set<Int> = sceneBuffers
        .filter { it.value.dirty }
        .map { it.key }
        .toSet()

    /**
     * This needs to be called after instantiation
     */
    fun initializeProjectEditor(): ProjectEditorRepository {
        val root = loadSceneTree()
        sceneTree.setRoot(root)

        cleanupSceneOrder()

        idRepository.findNextId()

        // Load any existing temp scenes into buffers
        val tempContent = getSceneTempBufferContents()
        for (content in tempContent) {
            val buffer = SceneBuffer(content, true)
            updateSceneBuffer(buffer)
        }

        reloadScenes()

        metadata = loadMetadata()

        contentUpdateJob = editorScope.launch {
            contentFlow.debounceUntilQuiescent(500.milliseconds).collect { content ->
                if (updateSceneBufferContent(content)) {
                    launchSaveJob(content.scene)
                }
            }
        }

        return this
    }

    abstract fun getSceneFilename(path: HPath): String
    abstract fun getSceneParentPath(path: HPath): ScenePathSegments
    abstract fun getScenePathSegments(path: HPath): ScenePathSegments
    abstract fun getSceneFilePath(sceneId: Int): HPath
    abstract fun getSceneDirectory(): HPath
    abstract fun getSceneBufferDirectory(): HPath
    abstract fun getSceneFilePath(sceneItem: SceneItem, isNewScene: Boolean = false): HPath
    abstract fun getSceneBufferTempPath(sceneItem: SceneItem): HPath
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
    abstract fun loadSceneBuffer(sceneItem: SceneItem): SceneBuffer
    abstract fun storeSceneBuffer(sceneItem: SceneItem): Boolean
    abstract fun storeTempSceneBuffer(sceneItem: SceneItem): Boolean
    abstract fun clearTempScene(sceneItem: SceneItem)
    abstract fun getLastOrderNumber(parentId: Int?): Int
    abstract fun getLastOrderNumber(parentPath: HPath): Int
    abstract fun updateSceneOrder(parentId: Int)
    abstract fun moveScene(moveRequest: MoveRequest)
    abstract fun renameScene(sceneItem: SceneItem, newName: String)

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
            _contentFlow.emit(content)
        }
    }

    private fun updateSceneBufferContent(content: SceneContent): Boolean {
        val oldBuffer = sceneBuffers[content.scene.id]
        // Skip update if nothing is different
        return if (content != oldBuffer?.content) {
            val newBuffer = SceneBuffer(content, true)
            updateSceneBuffer(newBuffer)
            true
        } else {
            false
        }
    }

    protected fun updateSceneBuffer(newBuffer: SceneBuffer) {
        sceneBuffers[newBuffer.content.scene.id] = newBuffer
        _bufferUpdateFlow.tryEmit(newBuffer)
    }

    fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? = sceneBuffers[sceneDef.id]
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

    fun getSceneIdFromPath(path: HPath): Int {
        val fileName = getSceneFilename(path)
        return getSceneIdFromFilename(fileName)
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

    protected abstract fun loadMetadata(): ProjectMetadata
    protected abstract fun saveMetadata(metadata: ProjectMetadata)
    protected abstract fun getMetadataPath(): HPath

    abstract fun getHpath(sceneItem: SceneItem): HPath

    override fun close() {
        contentUpdateJob?.cancel("Editor Closed")
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

        fun getSceneIdFromFilename(fileName: String): Int {
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

        fun getMetadataPath(projectDef: ProjectDef): HPath {
            return (projectDef.path.toOkioPath() / ProjectMetadata.FILENAME).toHPath()
        }
    }
}

fun Collection<HPath>.filterScenePaths() = filter {
    !it.name.startsWith(".")
}.sortedBy { it.name }

fun Sequence<HPath>.filterScenePaths() = filter {
    !it.name.startsWith(".")
}.sortedBy { it.name }

open class InvalidSceneFilename(message: String, fileName: String) :
    IllegalStateException("$fileName failed to parse because: $message")

class InvalidSceneBufferFilename(message: String, fileName: String) :
    InvalidSceneFilename(message, fileName)