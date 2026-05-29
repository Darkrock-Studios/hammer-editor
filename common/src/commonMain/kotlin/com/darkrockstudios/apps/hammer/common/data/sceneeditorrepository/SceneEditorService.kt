package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback

/**
 * The single Component-facing API for the scene-editing domain. Components and UI
 * talk only to this service (plus a few purpose-built UseCases such as
 * export/import); the underlying repositories are lower-level building blocks.
 *
 * The service owns write orchestration, derived scene-list state, and one-to-one
 * read delegations. Real logic and state live in the repositories — this is a
 * wide-but-thin door, not a god object.
 *
 * It composes [SceneRepository] (tree/paths/structure), [SceneContentRepository]
 * (buffers/autosave), and [SceneMetadataRepository] (metadata), and applies the
 * cross-cutting side-effects (statistics, writing-activity, reference index) that the
 * data repositories deliberately do not reach up to perform.
 */
class SceneEditorService(
	private val sceneEditorRepository: SceneEditorRepository,
	private val sceneContentRepository: SceneContentRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
	private val statisticsRepository: StatisticsRepository,
	private val writingSessionTracker: WritingSessionTracker,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope: ProjectDefScope get() = sceneEditorRepository.projectScope

	val projectDef: ProjectDef get() = sceneEditorRepository.projectDef

	private val dispatcherDefault by injectDefaultDispatcher()
	private val dispatcherIo by injectIoDispatcher()
	private val serviceScope = CoroutineScope(dispatcherDefault)

	init {
		projectScope.scope.registerCallback(this)
		// Autosave persists from the content repo's debounce engine fire here; run the
		// save side-effects (timestamp + stats) the content repo deliberately doesn't.
		serviceScope.launch {
			sceneContentRepository.bufferPersistedFlow.collect { event ->
				if (event.source == UpdateSource.Editor) {
					sceneMetadataRepository.recordSceneActivity(event.sceneId)
					statisticsRepository.markDirty()
				}
			}
		}
	}

	// region Writes / orchestration

	suspend fun createScene(
		parent: SceneItem?,
		sceneName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? = sceneEditorRepository.createScene(parent, sceneName, forceId, forceOrder)

	suspend fun createGroup(
		parent: SceneItem?,
		groupName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? = sceneEditorRepository.createGroup(parent, groupName, forceId, forceOrder)

	suspend fun deleteScene(scene: SceneItem): Boolean = sceneEditorRepository.deleteScene(scene)

	suspend fun deleteGroup(scene: SceneItem): Boolean = sceneEditorRepository.deleteGroup(scene)

	suspend fun renameScene(sceneItem: SceneItem, newName: String): Boolean =
		sceneEditorRepository.renameScene(sceneItem, newName)

	suspend fun moveScene(moveRequest: MoveRequest) = sceneEditorRepository.moveScene(moveRequest)

	suspend fun archiveScene(scene: SceneItem): Boolean = sceneEditorRepository.archiveScene(scene)

	suspend fun unarchiveScene(scene: SceneItem): SceneItem? =
		sceneEditorRepository.unarchiveScene(scene)

	/**
	 * Orchestrates a scene-metadata write: mark the scene's current identity for sync, persist
	 * via [SceneMetadataRepository], then apply the reference-index delta if the confirmed set
	 * changed. The repo handles only the pure persist + flow emission.
	 */
	suspend fun storeMetadata(metadata: SceneMetadata, sceneId: Int) {
		val scene = sceneEditorRepository.getSceneItemFromIdIncludingArchived(sceneId)
			?: error("storeMetadata: Failed to load scene for id: $sceneId ")

		val previous = sceneMetadataRepository.loadRawMetadata(sceneId)

		sceneEditorRepository.markSceneForSynchronization(scene)
		sceneMetadataRepository.storeMetadata(metadata, sceneId)

		val previousConfirmed = previous?.confirmedReferences.orEmpty()
		val newConfirmed = metadata.confirmedReferences
		if (previousConfirmed != newConfirmed) {
			referenceIndexRepository.applySceneDelta(
				sceneId = sceneId,
				added = newConfirmed - previousConfirmed,
				removed = previousConfirmed - newConfirmed,
			)
		}
	}

	/**
	 * Orchestrates a full buffer save: mark for sync, resolve the on-disk path, persist via
	 * [SceneContentRepository], then apply save side-effects (stats, writing-activity, timestamp).
	 */
	suspend fun storeSceneBuffer(sceneItem: SceneItem): Boolean {
		val buffer = sceneContentRepository.getSceneBuffer(sceneItem)
		if (buffer == null) {
			Napier.e { "Failed to store scene: ${sceneItem.id} - ${sceneItem.name}, no buffer present" }
			return false
		}

		sceneEditorRepository.markSceneForSynchronization(sceneItem)

		val scenePath = sceneEditorRepository.getSceneFilePath(sceneItem)
		val success = sceneContentRepository.persistBuffer(buffer, scenePath)

		if (success) {
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

	suspend fun storeAllBuffers() {
		sceneContentRepository.forEachDirtyBuffer { scene -> storeSceneBuffer(scene) }
	}

	fun discardSceneBuffer(sceneDef: SceneItem) {
		val scenePath = sceneEditorRepository.getSceneFilePath(sceneDef)
		val reloaded = sceneContentRepository.discardBuffer(sceneDef, scenePath)
		if (reloaded != null) {
			writingSessionTracker.rememberBaseline(sceneDef.id, reloaded.content.coerceMarkdown())
		}
	}

	fun onContentChanged(content: SceneContent, source: UpdateSource) =
		sceneContentRepository.onContentChanged(content, source)

	suspend fun storeSceneMarkdownRaw(sceneItem: SceneContent, scenePath: HPath? = null): Boolean =
		if (scenePath != null) {
			sceneEditorRepository.storeSceneMarkdownRaw(sceneItem, scenePath)
		} else {
			sceneEditorRepository.storeSceneMarkdownRaw(sceneItem)
		}

	// endregion

	// region Derived state

	val sceneListChannel: SharedFlow<SceneSummary> get() = sceneEditorRepository.sceneListChannel

	val metadataUpdateFlow: SharedFlow<Pair<Int, SceneMetadata>>
		get() = sceneMetadataRepository.metadataUpdateFlow

	fun getSceneSummaries(): SceneSummary = sceneEditorRepository.getSceneSummaries()

	fun subscribeToSceneUpdates(
		scope: CoroutineScope,
		onSceneListUpdate: (SceneSummary) -> Unit,
	): Job = sceneEditorRepository.subscribeToSceneUpdates(scope, onSceneListUpdate)

	fun subscribeToBufferUpdates(
		sceneDef: SceneItem?,
		scope: CoroutineScope,
		onBufferUpdate: suspend (SceneBuffer) -> Unit,
	): Job = sceneContentRepository.subscribeToBufferUpdates(sceneDef, scope, onBufferUpdate)

	// endregion

	// region Delegated reads

	fun getSceneTree(): ImmutableTree<SceneItem> = sceneEditorRepository.getSceneTree()

	fun getSceneItemFromId(id: Int): SceneItem? = sceneEditorRepository.getSceneItemFromId(id)

	fun getSceneItemFromIdIncludingArchived(id: Int): SceneItem? =
		sceneEditorRepository.getSceneItemFromIdIncludingArchived(id)

	fun getArchivedScenes(): List<SceneItem> = sceneEditorRepository.getArchivedScenes()

	fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? =
		sceneContentRepository.getSceneBuffer(sceneDef)

	/**
	 * Returns the cached buffer, or loads it from disk and remembers a writing-session baseline
	 * for the freshly-loaded content.
	 */
	fun loadSceneBuffer(sceneItem: SceneItem): SceneBuffer {
		val cached = sceneContentRepository.getSceneBuffer(sceneItem)
		if (cached != null) return cached

		val scenePath = sceneEditorRepository.getSceneFilePath(sceneItem)
		val buffer = sceneContentRepository.loadBuffer(sceneItem, scenePath)
		writingSessionTracker.rememberBaseline(sceneItem.id, buffer.content.coerceMarkdown())
		return buffer
	}

	suspend fun loadSceneBufferAsync(sceneItem: SceneItem): SceneBuffer = withContext(dispatcherIo) {
		loadSceneBuffer(sceneItem)
	}

	fun hasDirtyBuffers(): Boolean = sceneContentRepository.hasDirtyBuffers()

	suspend fun getMetadata(): ProjectMetadata = sceneMetadataRepository.getMetadata()

	suspend fun loadSceneMetadata(sceneId: Int): SceneMetadata =
		sceneMetadataRepository.loadSceneMetadata(sceneId)

	fun loadSceneMarkdownRaw(sceneItem: SceneItem, scenePath: HPath? = null): String =
		if (scenePath != null) {
			sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, scenePath)
		} else {
			sceneEditorRepository.loadSceneMarkdownRaw(sceneItem)
		}

	fun getSceneFilePathOrNull(sceneId: Int): HPath? =
		sceneEditorRepository.getSceneFilePathOrNull(sceneId)

	fun getSceneFilename(path: HPath): String = sceneEditorRepository.getSceneFilename(path)

	fun getPathSegments(sceneItem: SceneItem): List<Int> =
		sceneEditorRepository.getPathSegments(sceneItem)

	fun resolveScenePathFromFilesystem(id: Int): HPath? =
		sceneEditorRepository.resolveScenePathFromFilesystem(id)

	fun resolveScenePathFromFilesystemIncludingArchived(id: Int): HPath? =
		sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(id)

	fun validateSceneName(sceneName: String): CResult<Unit> =
		sceneEditorRepository.validateSceneName(sceneName)

	// endregion

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("Editor Closed")
	}
}
