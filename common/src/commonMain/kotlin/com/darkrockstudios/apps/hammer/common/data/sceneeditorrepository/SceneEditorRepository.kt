package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescentBy
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.IOException
import okio.Path
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class SceneEditorRepository(
	val projectDef: ProjectDef,
	private val idRepository: IdRepository,
	private val syncDataRepository: SyncDataRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
	private val sceneMetadataDatasource: SceneMetadataDatasource,
	private val sceneDatasource: SceneDatasource,
	private val statisticsRepository: StatisticsRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
	private val writingSessionTracker: WritingSessionTracker,
	private val clock: Clock,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	init {
		projectScope.scope.registerCallback(this)
	}

	val rootScene = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Root,
		id = SceneItem.ROOT_ID,
		name = "",
		order = 0
	)

	private val dispatcherMain by injectMainDispatcher()
	private val dispatcherDefault by injectDefaultDispatcher()
	private val dispatcherIo by injectIoDispatcher()
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

	private val sceneTree = Tree<SceneItem>()
	val rawTree: Tree<SceneItem>
		get() = sceneTree

	private suspend fun markForSynchronization(scene: SceneItem) {
		if (syncDataRepository.isServerSynchronized() && !syncDataRepository.isEntityDirty(scene.id)) {
			val metadata = sceneMetadataDatasource.loadMetadata(scene.id)
			val pathSegments = getPathSegments(scene)

			// For archived scenes, resolve path from filesystem since they're not in tree
			val content = if (scene.archived) {
				val scenePath = resolveScenePathFromFilesystemIncludingArchived(scene.id)
					?: error("Archived scene file not found for ID ${scene.id}")
				loadSceneMarkdownRaw(scene, scenePath)
			} else {
				loadSceneMarkdownRaw(scene)
			}

			val hash = EntityHasher.hashScene(
				id = scene.id,
				order = scene.order,
				path = pathSegments,
				name = scene.name,
				type = scene.type.toApiType(),
				content = content,
				outline = metadata?.outline ?: "",
				notes = metadata?.notes ?: "",
				archived = scene.archived,
				confirmedReferences = metadata?.confirmedReferences ?: emptySet(),
				dismissedReferences = metadata?.dismissedReferences ?: emptySet(),
				tags = metadata?.tags ?: emptySet(),
				created = metadata?.created,
				lastEdited = metadata?.lastEdited,
			)
			syncDataRepository.markEntityAsDirty(scene.id, hash)
		}
	}

	/**
	 * Public entry point for marking a scene's current persisted identity for sync.
	 * Used by [SceneEditorService] (which orchestrates metadata writes) and the sync layer,
	 * keeping the hashing logic (responsibility G) on the data layer.
	 */
	suspend fun markSceneForSynchronization(scene: SceneItem) = markForSynchronization(scene)

	// Runs through the whole tree and makes the scene order match the tree order
	// this fixes changes that were made else where or possibly due to crashes
	suspend fun cleanupSceneOrder() {
		val groups = sceneTree.filter {
			it.value.type == SceneItem.Type.Group ||
				it.value.type == SceneItem.Type.Root
		}

		groups.forEach { node ->
			try {
				updateSceneOrder(node.value.id)
			} catch (e: IOException) {
				Napier.e("Failed to clean up scene order for group ${node.value.id}", e)
			}
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

	private val sceneBuffersLock = reentrantLock()
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

	private fun getDirtyBufferIds(): Set<Int> = sceneBuffersLock.withLock {
		sceneBuffers
			.filter { it.value.dirty }
			.map { it.key }
			.toSet()
	}

	/**
	 * This needs to be called after instantiation
	 */
	suspend fun initializeSceneEditor(): SceneEditorRepository {
		val root = sceneDatasource.loadSceneTree(rootScene)
		sceneTree.setRoot(root)

		cleanupSceneOrder()

		idRepository.findNextId()

		// Load any existing temp scenes into buffers
		val tempContent = sceneDatasource.getSceneTempBufferContents()
		for (content in tempContent) {
			val buffer = SceneBuffer(content, true, UpdateSource.Repository)
			updateSceneBuffer(buffer)
		}

		reloadScenes()

		sceneMetadataRepository.initialize()

		contentUpdateJob = editorScope.launch {
			contentFlow.debounceUntilQuiescentBy({ it.content.scene.id }, BUFFER_COOL_DOWN)
				.collect { contentUpdate ->
					if (updateSceneBufferContent(contentUpdate.content, contentUpdate.source)) {
						launchSaveJob(contentUpdate.content.scene)
					}
				}
		}

		return this
	}

	/**
	 * This should only be used for server syncing
	 */
	internal fun forceSceneListReload() {
		reloadScenes()
	}

	fun getSceneSummaries(): SceneSummary {
		return SceneSummary(
			getSceneTree(),
			getDirtyBufferIds()
		)
	}

	fun reloadScenes(summary: SceneSummary? = null) {
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
		if (source == UpdateSource.Editor) {
			val newBuffer = SceneBuffer(content, dirty = true, source = source)
			updateSceneBuffer(newBuffer)
			return true
		}

		val oldBuffer = sceneBuffersLock.withLock { sceneBuffers[content.scene.id] }
		return if (content != oldBuffer?.content || content.platformRepresentation?.stateCompare(oldBuffer.content.platformRepresentation) == true) {
			val newBuffer = SceneBuffer(content, source != UpdateSource.Sync, source)
			updateSceneBuffer(newBuffer)
			true
		} else {
			false
		}
	}

	private fun updateSceneBuffer(newBuffer: SceneBuffer) {
		sceneBuffersLock.withLock {
			sceneBuffers[newBuffer.content.scene.id] = newBuffer
		}
		_bufferUpdateFlow.tryEmit(newBuffer)
	}

	fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? = getSceneBuffer(sceneDef.id)
	fun getSceneBuffer(sceneId: Int): SceneBuffer? = sceneBuffersLock.withLock { sceneBuffers[sceneId] }

	private fun hasSceneBuffer(sceneDef: SceneItem): Boolean =
		hasSceneBuffer(sceneDef.id)

	private fun hasSceneBuffer(sceneId: Int): Boolean =
		sceneBuffersLock.withLock { sceneBuffers.containsKey(sceneId) }

	fun hasDirtyBuffer(sceneId: Int): Boolean =
		getSceneBuffer(sceneId)?.dirty == true

	fun hasDirtyBuffers(): Boolean = sceneBuffersLock.withLock {
		sceneBuffers.any { it.value.dirty }
	}

	suspend fun storeAllBuffers() {
		val dirtyScenes = sceneBuffersLock.withLock {
			sceneBuffers.filter { it.value.dirty }.map { it.value.content.scene }
		}
		dirtyScenes.forEach { scene ->
			storeSceneBuffer(scene)
		}
	}

	fun discardSceneBuffer(sceneDef: SceneItem) {
		val wasPresent = sceneBuffersLock.withLock {
			if (sceneBuffers.containsKey(sceneDef.id)) {
				sceneBuffers.remove(sceneDef.id)
				true
			} else {
				false
			}
		}
		if (wasPresent) {
			clearTempScene(sceneDef)
			loadSceneBuffer(sceneDef)
		}
	}

	private fun willNextSceneIncreaseMagnitude(parentId: Int?): Boolean {
		val lastOrder = getLastOrderNumber(parentId)
		return lastOrder.numDigits() < (lastOrder + 1).numDigits()
	}

	/**
	 * Resolves the actual path to a parent scene/group from the filesystem.
	 * This is necessary because the calculated path (based on order padding) may differ
	 * from the actual filename on disk if the number of siblings has changed since creation.
	 *
	 * Falls back to calculated path if the item is not yet on the filesystem (new items).
	 */
	private fun resolveParentPathFromFilesystem(parentId: Int): HPath {
		val parent = getSceneItemFromId(parentId)
		return if (parent?.isRootScene == true) {
			sceneDatasource.getSceneDirectory()
		} else {
			sceneDatasource.resolveScenePathFromFilesystem(parentId)
				?: getSceneFilePath(parentId) // Fallback for items not yet on filesystem
		}
	}

	private fun getSceneFileName(
		sceneDef: SceneItem,
		isNewScene: Boolean = false
	): String {
		val parent = getSceneParentFromId(sceneDef.id)
		val parentId: Int = if (parent == null || parent.isRootScene) {
			rootScene.id
		} else {
			parent.id
		}
		// Resolve from filesystem to handle cases where padding doesn't match calculated value
		val parentPath = resolveParentPathFromFilesystem(parentId)

		val orderDigits = if (isNewScene && willNextSceneIncreaseMagnitude(parentId)) {
			sceneDatasource.getLastOrderNumber(parentPath).numDigits() + 1
		} else {
			sceneDatasource.getLastOrderNumber(parentPath).numDigits()
		}

		val order = sceneDef.order.toString().padStart(orderDigits, '0')
		val delim = ProjectsRepository.FILENAME_DELIMITER
		val encodedName = ProjectsRepository.encodeForFilename(sceneDef.name)
		val bareName = "$order$delim$encodedName$delim${sceneDef.id}"

		val filename = if (sceneDef.type == SceneItem.Type.Scene) {
			"$bareName${SceneDatasource.SCENE_FILENAME_EXTENSION}"
		} else {
			bareName
		}
		return filename
	}

	fun getSceneItemFromId(id: Int): SceneItem? {
		return sceneTree.findValueOrNull { it.id == id }
	}

	/**
	 * Get a scene by ID, checking both the active tree and archived scenes.
	 * Use this when you need to find a scene that might be archived (e.g., conflict resolution).
	 */
	fun getSceneItemFromIdIncludingArchived(id: Int): SceneItem? {
		return getSceneItemFromId(id)
			?: getArchivedScenes().find { it.id == id }
	}

	private fun getSceneNodeFromId(id: Int): TreeNode<SceneItem>? {
		return sceneTree.findOrNull { it.id == id }
	}

	fun getSceneParentFromId(id: Int): SceneItem? {
		return sceneTree.findOrNull { it.id == id }?.parent?.value
	}

	fun validateSceneName(sceneName: String): CResult<Unit> =
		ProjectsRepository.validateFileName(sceneName)

	override fun onScopeClose(scope: Scope) {
		contentUpdateJob?.cancel("Editor Closed")
		runBlocking {
			storeTempJobs.forEach { it.value.join() }
		}
		editorScope.cancel("Editor Closed")
		// During a proper shutdown, we clear any remaining temp buffers that haven't been saved yet
		sceneDatasource.getSceneTempBufferContents().forEach {
			clearTempScene(it.scene)
		}
		Napier.i("SceneEditorRepository Closed.")
	}

	fun getPathSegments(sceneItem: SceneItem): List<Int> {
		// Archived scenes have no hierarchy - they're flattened in .archived/
		if (sceneItem.archived) {
			return emptyList()
		}
		val hpath = getSceneFilePath(sceneItem.id)
		return getScenePathSegments(hpath).pathSegments
	}

	fun reIdScene(oldId: Int, newId: Int) {
		val oldScene = getSceneItemFromIdIncludingArchived(oldId)
			?: throw IOException("Scene $oldId does not exist")

		// For archived scenes, resolve path from filesystem since they're not in the tree
		val oldPath = if (oldScene.archived) {
			resolveScenePathFromFilesystemIncludingArchived(oldId)
				?: throw IOException("Could not resolve path for archived scene $oldId")
		} else {
			getSceneFilePath(oldId)
		}

		val newScene = oldScene.copy(id = newId)
		val newFileName = if (oldScene.archived) {
			SceneDatasource.buildArchivedSceneFileName(newScene.name, newScene.id)
		} else {
			getSceneFileName(newScene)
		}
		val parent = oldPath.toOkioPath().parent ?: error("Scene ID $oldId path had no parent")
		val newPath = (parent / newFileName).toHPath()

		sceneDatasource.moveScene(oldPath, newPath)

		sceneMetadataDatasource.reIdSceneMetadata(oldId = oldId, newId = newId)

		// Only update tree if the scene is not archived (archived scenes aren't in the tree)
		if (!oldScene.archived) {
			val node = getSceneNodeFromId(oldId) ?: error("reIdScene: Failed to get node for ID $oldId")
			node.value = node.value.copy(
				id = newId
			)
		}
	}

	fun getScenePathSegments(path: HPath): ScenePathSegments {
		val parentPath = path.toOkioPath()

		val sceneDir = sceneDatasource.getSceneDirectory().toOkioPath()
		return if (parentPath != sceneDir) {
			val sceneId = sceneDatasource.getSceneIdFromPath(path)
			val parentScenes = sceneTree.getBranch(true) { it.id == sceneId }
				.map { it.value.id }
			ScenePathSegments(pathSegments = parentScenes)
		} else {
			ScenePathSegments(pathSegments = emptyList())
		}
	}

	/**
	 * Transitional forwarder to [SceneMetadataRepository]. Repo-level readers still call this;
	 * they are repointed directly at the metadata repo in a later pass (REFACTOR doc, Phase D).
	 */
	suspend fun loadSceneMetadata(sceneId: Int): SceneMetadata =
		sceneMetadataRepository.loadSceneMetadata(sceneId)

	fun getSceneFilePath(sceneItem: SceneItem, isNewScene: Boolean = false): HPath {
		val scenePathSegment = getSceneDirectory().toOkioPath()

		val pathSegments: MutableList<String> = sceneTree
			.getBranch(true) { it.id == sceneItem.id }
			.map { node -> node.value }
			.filter { scene -> !scene.isRootScene }
			.map { scene -> getSceneFileName(scene) }
			.toMutableList()

		pathSegments.add(getSceneFileName(sceneItem, isNewScene))

		var fullPath: Path = scenePathSegment
		pathSegments.forEach { segment ->
			fullPath = fullPath.div(segment)
		}

		return fullPath.toHPath()
	}

	fun getSceneFilePath(sceneId: Int): HPath {
		val scenePathSegment = getSceneDirectory().toOkioPath()

		val branch = sceneTree.getBranch { it.id == sceneId }
		val pathSegments = branch
			.map { node -> node.value }
			.filter { sceneItem -> !sceneItem.isRootScene }
			.map { sceneItem -> getSceneFileName(sceneItem) }

		var fullPath: Path = scenePathSegment
		pathSegments.forEach { segment ->
			fullPath = fullPath.div(segment)
		}

		return fullPath.toHPath()
	}

	fun getSceneFilePathOrNull(sceneId: Int): HPath? {
		val branch = sceneTree.getBranchOrNull(excludeLeaf = false) { it.id == sceneId }
			?: return null

		val scenePathSegment = getSceneDirectory().toOkioPath()
		val pathSegments = branch
			.map { node -> node.value }
			.filter { sceneItem -> !sceneItem.isRootScene }
			.map { sceneItem -> getSceneFileName(sceneItem) }

		var fullPath: Path = scenePathSegment
		pathSegments.forEach { segment ->
			fullPath = fullPath.div(segment)
		}

		return fullPath.toHPath()
	}

	suspend fun renameScene(sceneItem: SceneItem, newName: String): Boolean {
		if (validateSceneName(newName).isFailure) return false

		markForSynchronization(sceneItem)

		val cleanedNamed = newName.trim()

		val oldPath = getSceneFilePath(sceneItem)
		val newDef = sceneItem.copy(name = cleanedNamed)

		val newPath = getSceneFilePath(newDef)

		sceneDatasource.moveScene(oldPath, newPath)

		val node = getSceneNodeFromId(sceneItem.id)
			?: error("Failed to get scene for renaming: ${sceneItem.id}")
		node.value = newDef

		reloadScenes()
		return true
	}

	private suspend fun updateSceneOrderMagnitudeOnly(parentId: Int) {
		Napier.d("updateSceneOrderMagnitudeOnly for parentId: $parentId")

		val parent = sceneTree.find { it.id == parentId }
		if (parent.value.type == SceneItem.Type.Scene) throw IllegalArgumentException("SceneItem must be Root or Group")

		// Resolve the actual path from the filesystem rather than calculating it.
		// The calculated path may have different zero-padding than the actual filename on disk.
		val parentPath = if (parent.value.isRootScene) {
			sceneDatasource.getSceneDirectory()
		} else {
			sceneDatasource.resolveScenePathFromFilesystem(parent.value.id)
				?: error("Could not find parent on filesystem: ${parent.value.id}")
		}
		val existingSceneFiles = sceneDatasource.getGroupChildPathsById(parentPath)

		parent.children().forEach { childNode ->
			val existingPath = existingSceneFiles[childNode.value.id]
				?: error("Scene wasn't present in directory")
			val newPath = getSceneFilePath(childNode.value.id)

			if (existingPath != newPath) {
				try {
					Napier.d("Renaming from: \"${existingPath.name}\" to: \"${newPath.name}\"")

					sceneDatasource.moveScene(sourcePath = existingPath, targetPath = newPath)
				} catch (e: IOException) {
					throw IOException("existingPath: $existingPath, newPath: $newPath", e)
				}
			}
		}
	}

	private suspend fun markForSynchronization(scene: SceneItem, content: String) {
		if (syncDataRepository.isServerSynchronized() && !syncDataRepository.isEntityDirty(scene.id)) {
			val metadata = sceneMetadataDatasource.loadMetadata(scene.id)
			val pathSegments = getPathSegments(scene)
			val hash = EntityHasher.hashScene(
				id = scene.id,
				order = scene.order,
				path = pathSegments,
				name = scene.name,
				type = scene.type.toApiType(),
				content = content,
				outline = metadata?.outline ?: "",
				notes = metadata?.notes ?: "",
				archived = scene.archived,
				confirmedReferences = metadata?.confirmedReferences ?: emptySet(),
				dismissedReferences = metadata?.dismissedReferences ?: emptySet(),
				tags = metadata?.tags ?: emptySet(),
				created = metadata?.created,
				lastEdited = metadata?.lastEdited,
			)
			syncDataRepository.markEntityAsDirty(scene.id, hash)
		}
	}

	suspend fun moveScene(moveRequest: MoveRequest) {
		val fromNode = sceneTree.find { it.id == moveRequest.id }
		val fromParentNode = fromNode.parent
			?: error("Item had no parent")

		val toParentNode = sceneTree[moveRequest.toPosition.coords.parentIndex]

		val isMovingParents = (fromParentNode != toParentNode)

		markForSynchronization(fromNode.value)

		// Perform move inside tree
		updateSceneTreeForMove(moveRequest)

		// Moving from one parent to another
		if (isMovingParents) {
			// Move the file to its new parent
			val toPath = getSceneFilePath(moveRequest.id)

			// Resolve the actual path from the filesystem rather than calculating it.
			// The calculated path may have different zero-padding than the actual filename on disk.
			val fromParentPath = if (fromParentNode.value.isRootScene) {
				sceneDatasource.getSceneDirectory()
			} else {
				sceneDatasource.resolveScenePathFromFilesystem(fromParentNode.value.id)
					?: error("Could not find from-parent on filesystem: ${fromParentNode.value.id}")
			}
			val originalFromParentScenePaths =
				sceneDatasource.getGroupChildPathsById(fromParentPath)
			val originalFromNodePath = originalFromParentScenePaths[fromNode.value.id]
				?: error("From node wasn't where it's supposed to be")

			sceneDatasource.moveScene(originalFromNodePath, toPath)

			// Update new parents children
			updateSceneOrder(toParentNode.value.id)

			// Update original parents children
			updateSceneOrder(fromParentNode.value.id)
		}
		// Moving inside same parent
		else {
			updateSceneOrder(toParentNode.value.id)
		}

		// Notify listeners of the new state of the tree
		val imTree = sceneTree.toImmutableTree()

		val newSummary = SceneSummary(
			imTree,
			getDirtyBufferIds()
		)
		reloadScenes(newSummary)
	}

	private suspend fun updateSceneTreeForMove(moveRequest: MoveRequest) {
		val fromNode = sceneTree.find { it.id == moveRequest.id }
		val toParentNode = sceneTree[moveRequest.toPosition.coords.parentIndex]
		val insertIndex = moveRequest.toPosition.coords.childLocalIndex

		Napier.d("Move Scene Item: $moveRequest")

		val fromParent = fromNode.parent
		val fromIndex = fromParent?.localIndexOf(fromNode) ?: -1
		val changingParents = (toParentNode != fromParent)

		val finalIndex = if (toParentNode.numChildrenImmedate() == 0) {
			0
		} else {
			if (!changingParents) {
				if (fromIndex <= insertIndex) {
					if (moveRequest.toPosition.before) {
						(insertIndex - 1).coerceAtLeast(0)
					} else {
						insertIndex
					}
				} else {
					if (moveRequest.toPosition.before) {
						insertIndex
					} else {
						insertIndex + 1
					}
				}
			} else {
				if (moveRequest.toPosition.before) {
					insertIndex
				} else {
					insertIndex + 1
				}
			}
		}

		markForSynchronization(fromNode.value)

		toParentNode.insertChild(finalIndex, fromNode)

		/*
		// Move debugging
		println("Before Move:")
		sceneTree.print()

		println("After Move:")
		sceneTree.print()
		*/
	}

	fun getSceneDirectory() = sceneDatasource.getSceneDirectory()

	private suspend fun updateSceneOrder(parentId: Int) {
		val parent = sceneTree.find { it.id == parentId }
		if (parent.value.type == SceneItem.Type.Scene) throw IllegalArgumentException("SceneItem must be Root or Group")

		// Resolve the actual path from the filesystem rather than calculating it from the tree.
		// The calculated path may have different zero-padding than the actual filename on disk.
		val parentPath = if (parent.value.isRootScene) {
			sceneDatasource.getSceneDirectory()
		} else {
			sceneDatasource.resolveScenePathFromFilesystem(parent.value.id)
				?: error("Could not find parent on filesystem: ${parent.value.id}")
		}
		val existingSceneFiles = sceneDatasource.getGroupChildPathsById(parentPath)

		// Must grab a copy of the children before they are modified
		// we'll need this if we need to calculate their original hash
		// down below for markForSynchronization()
		val originalChildren = if (syncDataRepository.isServerSynchronized()) {
			parent.children().map { child -> child.value.copy() }
		} else {
			null
		}

		parent.children().forEachIndexed { index, childNode ->
			childNode.value = childNode.value.copy(order = index)

			val existingPath = existingSceneFiles[childNode.value.id]
				?: error("Scene wasn't present in directory")
			val newPath = getSceneFilePath(childNode.value.id)

			if (existingPath != newPath) {
				try {
					originalChildren?.find { it.id == childNode.value.id }?.let { originalChild ->
						val realPath = sceneDatasource.getPathFromFilesystem(childNode.value)
							?: error("Could not find Scene on filesystem: ${childNode.value.id}")

						val content = loadSceneMarkdownRaw(childNode.value, realPath)
						markForSynchronization(originalChild, content)
					}
					sceneDatasource.moveScene(sourcePath = existingPath, targetPath = newPath)
				} catch (e: IOException) {
					throw IOException("existingPath: $existingPath, newPath: $newPath", e)
				}
			}
		}
	}

	// Used after a server sync
	private fun correctSceneOrders() {
		correctSceneOrders(sceneTree.root())
	}

	/**
	 * Walks the scene tree and makes the order of the children
	 * in the tree match their internal `order` property.
	 *
	 * This is only used when server syncing has changed orders.
	 */
	private fun correctSceneOrders(node: TreeNode<SceneItem>) {
		val children = node.children()
		val sortedChildren = children.sortedBy { it.value.order }

		for (i in children.indices) {
			val child = children.first()
			node.removeChild(child)
		}

		sortedChildren.forEach { child -> node.addChild(child) }

		children.forEach { child ->
			if (child.numChildrenImmedate() > 0) {
				correctSceneOrders(child)
			}
		}
	}

	/**
	 * This looks at the in-memory tree and checks it against the filesystem.
	 * Any discrepancies it finds on the filesystem will be corrected so that
	 * it matches the tree.
	 */
	fun rationalizeTree() {
		correctSceneOrders()

		sceneTree.forEach { node ->
			if (node.value.type == SceneItem.Type.Root) return@forEach

			val intendedPath = getSceneFilePath(node.value.id)

			val allPaths = sceneDatasource.getAllScenePaths()
			val realPath = allPaths.find { path ->
				val scene = sceneDatasource.getSceneFromPath(path)
				scene.id == node.value.id
			}

			if (realPath != null) {
				if (realPath != intendedPath) {
					Napier.i { "Moving scene to new path: ${intendedPath.path} from old path: ${realPath.path}" }
					sceneDatasource.moveScene(realPath, intendedPath)
				} else {
					// Too chatty, don't need it
					//Napier.d { "Scene ${node.value.id} is in the correct location" }
				}
			} else {
				Napier.e { "Scene ${node.value.id} is missing from the filesystem" }
			}
		}
	}

	suspend fun createScene(
		parent: SceneItem?,
		sceneName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? {
		return createSceneItem(parent, sceneName, false, forceId, forceOrder)
	}

	suspend fun createGroup(
		parent: SceneItem?,
		groupName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? {
		return createSceneItem(parent, groupName, true, forceId, forceOrder)
	}

	private suspend fun createSceneItem(
		parent: SceneItem?,
		name: String,
		isGroup: Boolean,
		forceId: Int?,
		forceOrder: Int?,
	): SceneItem? {
		val cleanedNamed = name.trim()

		return if (validateSceneName(cleanedNamed).isFailure) {
			Napier.d("Invalid scene name")
			null
		} else {
			val lastOrder = getLastOrderNumber(parent?.id)
			val nextOrder = forceOrder ?: (lastOrder + 1)
			val sceneId = forceId ?: idRepository.claimNextId()
			val type = if (isGroup) SceneItem.Type.Group else SceneItem.Type.Scene

			val newSceneItem = SceneItem(
				projectDef = projectDef,
				type = type,
				id = sceneId,
				name = cleanedNamed,
				order = nextOrder,
			)

			val newTreeNode = TreeNode(newSceneItem)
			if (parent != null) {
				val parentNode = sceneTree.find { it.id == parent.id }
				parentNode.addChild(newTreeNode)
			} else {
				sceneTree.addChild(newTreeNode)
			}

			val scenePath = getSceneFilePath(newSceneItem, true)
			when (type) {
				SceneItem.Type.Scene -> sceneDatasource.createNewGroup(scenePath)
				SceneItem.Type.Group -> sceneDatasource.createNewScene(scenePath)
				SceneItem.Type.Root -> throw IllegalArgumentException("Cannot create Root")
			}

			if (type == SceneItem.Type.Scene) {
				val now = clock.now()
				sceneMetadataDatasource.storeMetadata(
					SceneMetadata(created = now, lastEdited = now),
					sceneId,
				)
			}

			// Correct order digit paddings when injecting a new scene/group
			if (forceOrder != null) {
				updateSceneOrderMagnitudeOnly(parent?.id ?: SceneItem.ROOT_ID)
			}
			// If we need to increase the padding digits, update the file names
			else if (lastOrder.numDigits() < nextOrder.numDigits()) {
				updateSceneOrder(parent?.id ?: SceneItem.ROOT_ID)
			}

			Napier.i("createScene: $cleanedNamed")

			reloadScenes()
			statisticsRepository.markDirty()

			newSceneItem
		}
	}

	suspend fun deleteScene(scene: SceneItem): Boolean {
		val deleted = sceneDatasource.deleteScene(scene)

		return if (deleted) {
			val sceneNode = getSceneNodeFromId(scene.id)

			val parent = sceneNode?.parent
			if (parent != null) {
				val parentId: Int = parent.value.id
				parent.removeChild(sceneNode)

				updateSceneOrder(parentId)
				Napier.w("Scene ${scene.id} deleted")

				if (syncDataRepository.isServerSynchronized()) {
					syncDataRepository.recordIdDeletion(scene.id)
				}

				reloadScenes()
				statisticsRepository.markDirty()
				referenceIndexRepository.markSceneDeleted(scene.id)
				writingSessionTracker.forgetBaseline(scene.id)

				true
			} else {
				Napier.w("Partially failed to delete scene ${scene.id}")
				false
			}
		} else {
			deleted
		}
	}

	suspend fun deleteGroup(scene: SceneItem): Boolean {
		val deleted = sceneDatasource.deleteGroup(scene)

		return if (deleted) {
			if (syncDataRepository.isServerSynchronized()) {
				syncDataRepository.recordIdDeletion(scene.id)
			}

			val sceneNode = getSceneNodeFromId(scene.id)

			val parent = sceneNode?.parent
			if (parent != null) {
				val parentId: Int = parent.value.id
				parent.removeChild(sceneNode)

				updateSceneOrder(parentId)
				Napier.w("Group ${scene.id} deleted")

				reloadScenes()
				statisticsRepository.markDirty()

				true
			} else {
				Napier.w("Failed to delete group ${scene.id}")
				false
			}
		} else {
			deleted
		}
	}

	fun getScenes(): List<SceneItem> = sceneDatasource.getAllScenes()

	fun getSceneTree(): ImmutableTree<SceneItem> {
		return sceneTree.toImmutableTree()
	}

	/**
	 * This should only be used for stats and other fire and forget actions where accuracy
	 * and integrity of the data is not important.
	 * Anything that wishes to interact with scene content should use `loadSceneBuffer`
	 * instead.
	 */
	fun loadSceneMarkdownRaw(
		sceneItem: SceneItem,
		scenePath: HPath = getSceneFilePath(sceneItem)
	): String =
		sceneDatasource.loadSceneMarkdownRaw(sceneItem, scenePath)

	/**
	 * This should only be used for server syncing
	 */
	suspend fun storeSceneMarkdownRaw(
		sceneItem: SceneContent,
		scenePath: HPath = getSceneFilePath(sceneItem.scene)
	): Boolean {
		markForSynchronization(sceneItem.scene)

		val success = sceneDatasource.storeSceneMarkdownRaw(sceneItem, scenePath)
		return success
	}

	fun loadSceneBuffer(sceneItem: SceneItem): SceneBuffer {
		val cachedBuffer = getSceneBuffer(sceneItem)
		return if (cachedBuffer != null) {
			cachedBuffer
		} else {
			val scenePath = getSceneFilePath(sceneItem)
			val content = sceneDatasource.loadSceneBuffer(scenePath)
			writingSessionTracker.rememberBaseline(sceneItem.id, content)
			val newBuffer = SceneBuffer(
				SceneContent(sceneItem, content),
				source = UpdateSource.Repository
			)

			updateSceneBuffer(newBuffer)

			newBuffer
		}
	}

	suspend fun loadSceneBufferAsync(sceneItem: SceneItem): SceneBuffer = withContext(dispatcherIo) {
		loadSceneBuffer(sceneItem)
	}

	suspend fun storeSceneBuffer(sceneItem: SceneItem): Boolean {
		val buffer = getSceneBuffer(sceneItem)
		if (buffer == null) {
			Napier.e { "Failed to store scene: ${sceneItem.id} - ${sceneItem.name}, no buffer present" }
			return false
		}

		markForSynchronization(sceneItem)

		val scenePath = getSceneFilePath(sceneItem)
		val success = sceneDatasource.storeSceneBuffer(buffer, scenePath)

		if (success) {
			val cleanBuffer = buffer.copy(dirty = false)
			updateSceneBuffer(cleanBuffer)

			clearTempScene(sceneItem)
			statisticsRepository.markDirty()
			writingSessionTracker.onSceneSaved(
				sceneId = sceneItem.id,
				newContent = buffer.content.coerceMarkdown(),
				source = buffer.source,
			)
			if (buffer.source == UpdateSource.Editor) {
				sceneMetadataRepository.recordSceneActivity(sceneItem.id)
			}
		}

		return success
	}

	private suspend fun storeTempSceneBuffer(sceneItem: SceneItem): Boolean {
		val buffer = getSceneBuffer(sceneItem)
		if (buffer == null) {
			Napier.e { "Failed to store scene: ${sceneItem.id} - ${sceneItem.name}, no buffer present" }
			return false
		}
		val success = sceneDatasource.storeTempSceneBuffer(buffer)
		if (success && buffer.source == UpdateSource.Editor) {
			sceneMetadataRepository.recordSceneActivity(sceneItem.id)
			statisticsRepository.markDirty()
		}
		return success
	}

	private fun clearTempScene(sceneItem: SceneItem) = sceneDatasource.clearTempScene(sceneItem)

	private fun getLastOrderNumber(parentId: Int?): Int {
		val parentPath: HPath = if (parentId != null && parentId != 0) {
			val parentItem =
				getSceneItemFromId(parentId) ?: error("Parent not found")

			getSceneFilePath(parentItem)
		} else {
			getSceneDirectory()
		}

		val numScenes = sceneDatasource.countScenes(parentPath)
		return numScenes
	}

	fun getSceneFilename(path: HPath) = sceneDatasource.getSceneFilename(path)

	/**
	 * This is much slower than using the Scene Tree, but some times you need it.
	 * It goes right to the source of truth, the disk.
	 */
	fun resolveScenePathFromFilesystem(id: Int) = sceneDatasource.resolveScenePathFromFilesystem(id)

	/**
	 * Resolves the scene path from filesystem, including archived scenes.
	 */
	fun resolveScenePathFromFilesystemIncludingArchived(id: Int) =
		sceneDatasource.resolveScenePathFromFilesystemIncludingArchived(id)

	/**
	 * Archive a scene, moving it to the .archived directory.
	 * Only individual scenes can be archived, not groups.
	 * The scene will be removed from the scene tree and marked for sync.
	 */
	suspend fun archiveScene(scene: SceneItem): Boolean {
		if (scene.type != SceneItem.Type.Scene) {
			Napier.w("Cannot archive non-scene: ${scene.id}")
			return false
		}

		// Mark dirty before modification (with current state for proper hash)
		markForSynchronization(scene)

		// Remove from tree
		val node = getSceneNodeFromId(scene.id) ?: return false
		val parent = node.parent ?: return false
		val parentId = parent.value.id
		parent.removeChild(node)

		sceneDatasource.archiveScene(scene)

		// Update sibling orders
		updateSceneOrder(parentId)

		reloadScenes()
		statisticsRepository.markDirty()

		Napier.i("Scene ${scene.id} archived")
		return true
	}

	/**
	 * Unarchive a scene, moving it from the .archived directory back to the scenes root.
	 * The scene will be added at the end of the root level.
	 */
	suspend fun unarchiveScene(scene: SceneItem): SceneItem? {
		if (!scene.archived) {
			Napier.w("Scene ${scene.id} is not archived")
			return null
		}

		// Calculate new order (append at end of root)
		val lastOrder = getLastOrderNumber(null)
		val newOrder = lastOrder + 1

		sceneDatasource.unarchiveScene(scene, newOrder)

		val unarchivedScene = scene.copy(
			order = newOrder,
			archived = false
		)

		// Add to tree at root level
		val newNode = TreeNode(unarchivedScene)
		sceneTree.root().addChild(newNode)

		// SceneDatasource.unarchiveScene writes the file with an unpadded order. Update if needed.
		if (lastOrder.numDigits() < newOrder.numDigits()) {
			updateSceneOrder(SceneItem.ROOT_ID)
		}

		markForSynchronization(unarchivedScene)

		reloadScenes()
		statisticsRepository.markDirty()

		Napier.i("Scene ${scene.id} unarchived")
		return unarchivedScene
	}

	/**
	 * Get all archived scenes from the .archived directory.
	 */
	fun getArchivedScenes(): List<SceneItem> {
		return sceneDatasource.getArchivedScenes()
	}

	fun getArchivedSceneFromId(id: Int): SceneItem? {
		return getArchivedScenes().find { it.id == id }
	}

	/**
	 * Create a scene directly in the archive directory.
	 * Used when syncing an archived scene from the server that doesn't exist locally.
	 */
	suspend fun createArchivedScene(
		id: Int,
		name: String,
		order: Int,
		type: SceneItem.Type,
		content: String,
		metadata: SceneMetadata
	): SceneItem {
		val sceneItem = SceneItem(
			projectDef = projectDef,
			type = type,
			id = id,
			name = name,
			order = order,
			archived = true
		)

		sceneDatasource.createArchivedSceneFile(sceneItem, content)
		sceneMetadataDatasource.storeMetadata(metadata, id)

		return sceneItem
	}

	companion object {
		val BUFFER_COOL_DOWN = 500.milliseconds
	}
}