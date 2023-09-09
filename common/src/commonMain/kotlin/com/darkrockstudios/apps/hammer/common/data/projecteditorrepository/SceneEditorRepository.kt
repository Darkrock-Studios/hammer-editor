package com.darkrockstudios.apps.hammer.common.data.projecteditorrepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescentBy
import com.darkrockstudios.apps.hammer.common.util.numDigits
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import okio.Closeable
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.milliseconds

abstract class SceneEditorRepository(
	val projectDef: ProjectDef,
	protected val idRepository: IdRepository,
	protected val projectSynchronizer: ClientProjectSynchronizer,
	protected val metadataRepository: ProjectMetadataRepository,
) : Closeable, KoinComponent {

	val rootScene = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Root,
		id = SceneItem.ROOT_ID,
		name = "",
		order = 0
	)

	private val metadata = MutableSharedFlow<ProjectMetadata>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)

	suspend fun getMetadata(): ProjectMetadata {
		return metadata.first()
	}

	protected val dispatcherMain by injectMainDispatcher()
	protected val dispatcherDefault by injectDefaultDispatcher()
	private val editorScope = CoroutineScope(dispatcherDefault)

	private val _contentFlow = MutableSharedFlow<SceneContentUpdate>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	private val contentFlow: SharedFlow<SceneContentUpdate> = _contentFlow
	private var contentUpdateJob: Job? = null

	private val _bufferUpdateFlow = MutableSharedFlow<SceneBuffer>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	private val bufferUpdateFlow: SharedFlow<SceneBuffer> = _bufferUpdateFlow

	private val _sceneListChannel = MutableSharedFlow<SceneSummary>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val sceneListChannel: SharedFlow<SceneSummary> = _sceneListChannel

	protected val sceneTree = Tree<SceneItem>()
	val rawTree: Tree<SceneItem>
		get() = sceneTree

	protected abstract fun loadSceneTree(): TreeNode<SceneItem>

	protected suspend fun markForSynchronization(scene: SceneItem) {
		if (projectSynchronizer.isServerSynchronized() && !projectSynchronizer.isEntityDirty(scene.id)) {
			val pathSegments = getPathSegments(scene)
			val content = loadSceneMarkdownRaw(scene)
			val hash = EntityHasher.hashScene(
				id = scene.id,
				order = scene.order,
				path = pathSegments,
				name = scene.name,
				type = scene.type.toApiType(),
				content = content
			)
			projectSynchronizer.markEntityAsDirty(scene.id, hash)
		}
	}

	// Runs through the whole tree and makes the scene order match the tree order
	// this fixes changes that were made else where or possibly due to crashes
	suspend fun cleanupSceneOrder() {
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
		onBufferUpdate: suspend (SceneBuffer) -> Unit
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
	suspend fun initializeProjectEditor(): SceneEditorRepository {
		val root = loadSceneTree()
		sceneTree.setRoot(root)

		cleanupSceneOrder()

		idRepository.findNextId()

		// Load any existing temp scenes into buffers
		val tempContent = getSceneTempBufferContents()
		for (content in tempContent) {
			val buffer = SceneBuffer(content, true, UpdateSource.Repository)
			updateSceneBuffer(buffer)
		}

		reloadScenes()

		val newMetadata = metadataRepository.loadMetadata(projectDef)
		metadata.emit(newMetadata)

		contentUpdateJob = editorScope.launch {
			contentFlow.debounceUntilQuiescentBy({ it.content.scene.id }, BUFFER_COOL_DOWN).collect { contentUpdate ->
				if (updateSceneBufferContent(contentUpdate.content, contentUpdate.source)) {
					launchSaveJob(contentUpdate.content.scene)
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
	abstract suspend fun createScene(
		parent: SceneItem?,
		sceneName: String,
		forceId: Int? = null,
		forceOrder: Int? = null
	): SceneItem?

	abstract suspend fun createGroup(
		parent: SceneItem?,
		groupName: String,
		forceId: Int? = null,
		forceOrder: Int? = null
	): SceneItem?

	abstract suspend fun deleteScene(scene: SceneItem): Boolean
	abstract suspend fun deleteGroup(scene: SceneItem): Boolean
	abstract fun getScenes(): List<SceneItem>
	abstract fun getSceneTree(): ImmutableTree<SceneItem>
	abstract fun getScenes(root: HPath): List<SceneItem>
	abstract fun getSceneTempBufferContents(): List<SceneContent>
	abstract fun getSceneAtIndex(index: Int): SceneItem
	abstract fun getSceneFromPath(path: HPath): SceneItem
	abstract fun exportStory(path: HPath): HPath
	abstract fun getExportStoryFileName(): String

	/**
	 * This should only be used for stats and other fire and forget actions where accuracy
	 * and integrity of the data is not important.
	 * Anything that wishes to interact with scene content should use `loadSceneBuffer`
	 * instead.
	 */
	abstract fun loadSceneMarkdownRaw(sceneItem: SceneItem, scenePath: HPath = getSceneFilePath(sceneItem)): String

	/**
	 * This should only be used for server syncing
	 */
	abstract suspend fun storeSceneMarkdownRaw(
		sceneItem: SceneContent,
		scenePath: HPath = getSceneFilePath(sceneItem.scene)
	): Boolean

	abstract fun getPathFromFilesystem(sceneItem: SceneItem): HPath?

	/**
	 * This should only be used for server syncing
	 */
	internal fun forceSceneListReload() {
		reloadScenes()
	}

	abstract fun loadSceneBuffer(sceneItem: SceneItem): SceneBuffer
	abstract suspend fun storeSceneBuffer(sceneItem: SceneItem): Boolean
	abstract fun storeTempSceneBuffer(sceneItem: SceneItem): Boolean
	abstract fun clearTempScene(sceneItem: SceneItem)
	abstract fun getLastOrderNumber(parentId: Int?): Int
	abstract fun getLastOrderNumber(parentPath: HPath): Int
	abstract suspend fun updateSceneOrder(parentId: Int)
	abstract suspend fun moveScene(moveRequest: MoveRequest)
	abstract suspend fun renameScene(sceneItem: SceneItem, newName: String)

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

	fun onContentChanged(content: SceneContent, source: UpdateSource) {
		editorScope.launch {
			val update = SceneContentUpdate(content, source)
			_contentFlow.emit(update)
		}
	}

	private fun updateSceneBufferContent(content: SceneContent, source: UpdateSource): Boolean {
		val oldBuffer = sceneBuffers[content.scene.id]
		// Skip update if nothing is different
		return if (content != oldBuffer?.content) {
			val newBuffer = SceneBuffer(content, source != UpdateSource.Sync, source)
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

	fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? = getSceneBuffer(sceneDef.id)
	fun getSceneBuffer(sceneId: Int): SceneBuffer? = sceneBuffers[sceneId]

	protected fun hasSceneBuffer(sceneDef: SceneItem): Boolean =
		hasSceneBuffer(sceneDef.id)

	protected fun hasSceneBuffer(sceneId: Int): Boolean =
		sceneBuffers.containsKey(sceneId)

	protected fun hasDirtyBuffer(sceneDef: SceneItem): Boolean =
		hasDirtyBuffer(sceneDef.id)

	protected fun hasDirtyBuffer(sceneId: Int): Boolean =
		getSceneBuffer(sceneId)?.dirty == true

	fun hasDirtyBuffers(): Boolean = sceneBuffers.any { it.value.dirty }

	suspend fun storeAllBuffers() {
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

	fun validateSceneName(sceneName: String): CResult<Unit> = ProjectsRepository.validateFileName(sceneName)

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

	fun getPathSegments(sceneItem: SceneItem): List<Int> {
		val hpath = getSceneFilePath(sceneItem.id)
		return getScenePathSegments(hpath).pathSegments
	}

	abstract fun rationalizeTree()
	abstract fun reIdScene(oldId: Int, newId: Int)

	companion object {
		val SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\d\p{L}+ _']+)-(\d+)(\.md)?(?:\.temp)?""")
		val SCENE_BUFFER_FILENAME_PATTERN = Regex("""(\d+)\.md""")
		const val SCENE_FILENAME_EXTENSION = ".md"
		const val SCENE_DIRECTORY = "scenes"
		const val BUFFER_DIRECTORY = ".buffers"
		val BUFFER_COOL_DOWN = 500.milliseconds

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
	}

	abstract suspend fun updateSceneOrderMagnitudeOnly(parentId: Int)
	abstract fun resolveScenePathFromFilesystem(id: Int): HPath?
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

inline fun Tree<SceneItem>.findById(scene: SceneItem): TreeNode<SceneItem> = findById(scene.id)
inline fun Tree<SceneItem>.findById(id: Int): TreeNode<SceneItem> = find { it.id == id }