package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
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
	private val sceneEditorRepository: SceneRepository,
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
	private val dispatcherMain by injectMainDispatcher()
	private val serviceScope = CoroutineScope(dispatcherDefault)

	init {
		projectScope.scope.registerCallback(this)
		// Autosave persists from the content repo's debounce engine fire here; run the
		// save side-effects (writing-activity, timestamp, stats) the content repo
		// deliberately doesn't. Crediting writing activity here — not only on full
		// saves — means active typing counts as it happens, instead of waiting for the
		// next sync, scene switch, or app close to flush the buffer.
		serviceScope.launch {
			sceneContentRepository.bufferPersistedFlow.collect { event ->
				if (event.source == UpdateSource.Editor) {
					sceneContentRepository.getSceneBuffer(event.sceneId)?.let { buffer ->
						writingSessionTracker.onSceneSaved(
							sceneId = event.sceneId,
							newContent = buffer.content.coerceMarkdown(),
							source = event.source,
						)
					}
					sceneMetadataRepository.recordSceneActivity(event.sceneId)
					statisticsRepository.markDirty()
				}
			}
		}
	}

	// region Writes / orchestration

	/**
	 * Project-open entry point: loads the scene tree, then starts the content (autosave) and
	 * metadata engines, in order.
	 */
	suspend fun initialize() {
		sceneEditorRepository.initializeSceneEditor()
		sceneContentRepository.initialize()
		sceneMetadataRepository.initialize()
	}

	suspend fun createScene(
		parent: SceneItem?,
		sceneName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? {
		val created = sceneEditorRepository.createScene(parent, sceneName, forceId, forceOrder)
		if (created != null) statisticsRepository.markDirty()
		return created
	}

	suspend fun createGroup(
		parent: SceneItem?,
		groupName: String,
		forceId: Int? = null,
		forceOrder: Int? = null,
	): SceneItem? {
		val created = sceneEditorRepository.createGroup(parent, groupName, forceId, forceOrder)
		if (created != null) statisticsRepository.markDirty()
		return created
	}

	suspend fun deleteScene(scene: SceneItem): Boolean {
		val deleted = sceneEditorRepository.deleteScene(scene)
		if (deleted) {
			statisticsRepository.markDirty()
			referenceIndexRepository.markSceneDeleted(scene.id)
			writingSessionTracker.forgetBaseline(scene.id)
			// A leftover temp file would attach itself to a later scene that reuses this id.
			sceneContentRepository.dropBuffer(scene)
		}
		return deleted
	}

	suspend fun deleteGroup(scene: SceneItem): Boolean {
		val deleted = sceneEditorRepository.deleteGroup(scene)
		if (deleted) statisticsRepository.markDirty()
		return deleted
	}

	suspend fun renameScene(sceneItem: SceneItem, newName: String): Boolean =
		sceneEditorRepository.renameScene(sceneItem, newName)

	suspend fun moveScene(moveRequest: MoveRequest) = sceneEditorRepository.moveScene(moveRequest)

	suspend fun archiveScene(scene: SceneItem): Boolean {
		val archived = sceneEditorRepository.archiveScene(scene)
		if (archived) statisticsRepository.markDirty()
		return archived
	}

	suspend fun unarchiveScene(scene: SceneItem): SceneItem? {
		val unarchived = sceneEditorRepository.unarchiveScene(scene)
		if (unarchived != null) statisticsRepository.markDirty()
		return unarchived
	}

	/**
	 * Orchestrates a scene-metadata write: mark the scene's current identity for sync, persist
	 * via [SceneMetadataRepository], then apply the reference-index delta if the confirmed set
	 * changed. The repo handles only the pure persist + flow emission. A write applying server
	 * state ([UpdateSource.Sync]) is not a local change, so it is not marked.
	 */
	suspend fun storeMetadata(
		metadata: SceneMetadata,
		sceneId: Int,
		source: UpdateSource = UpdateSource.Editor,
	) {
		// A flush can arrive after the scene was deleted (e.g. the metadata panel's save on
		// destroy). Nothing to persist for a scene that no longer exists.
		val scene = sceneEditorRepository.getSceneItemFromIdIncludingArchived(sceneId)
			?: return

		val previous = sceneMetadataRepository.loadRawMetadata(sceneId)

		if (source != UpdateSource.Sync) sceneEditorRepository.markSceneForSynchronization(scene)
		sceneMetadataRepository.storeMetadata(metadata, sceneId, source)

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

		// Outside the active tree an archived scene still has its file; a deleted one has nowhere to save.
		val scenePath = if (sceneEditorRepository.getSceneItemFromId(sceneItem.id) != null) {
			sceneEditorRepository.resolveSceneContentPath(sceneItem)
		} else {
			sceneEditorRepository.resolveScenePathFromFilesystemIncludingArchived(sceneItem.id)
		}
		if (scenePath == null) {
			Napier.w { "Dropping buffer for scene ${sceneItem.id}, it no longer exists" }
			sceneContentRepository.dropBuffer(sceneItem)
			writingSessionTracker.forgetBaseline(sceneItem.id)
			return false
		}

		sceneEditorRepository.markSceneForSynchronization(sceneItem)

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
		// An archived or deleted scene has no active path to reload from.
		if (sceneEditorRepository.getSceneItemFromId(sceneDef.id) == null) {
			sceneContentRepository.dropBuffer(sceneDef)
			writingSessionTracker.forgetBaseline(sceneDef.id)
			return
		}
		val scenePath = sceneEditorRepository.resolveSceneContentPath(sceneDef)
		val reloaded = sceneContentRepository.discardBuffer(sceneDef, scenePath)
		if (reloaded != null) {
			writingSessionTracker.rememberBaseline(sceneDef.id, reloaded.content.coerceMarkdown())
		}
	}

	/** Reverts every dirty buffer to its on-disk content. */
	fun discardAllBuffers() {
		sceneContentRepository.getDirtyBufferScenes().forEach { discardSceneBuffer(it) }
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

	/**
	 * The scene list: the structural tree (from [SceneRepository]) combined with the dirty-buffer
	 * set (from [SceneContentRepository]). Re-emits when either changes, so dirty markers update live.
	 */
	val sceneListChannel: SharedFlow<SceneSummary> = combine(
		sceneEditorRepository.sceneTreeUpdates,
		sceneContentRepository.dirtyBufferIds,
	) { tree, dirtyBufferIds -> SceneSummary(tree, dirtyBufferIds) }
		.shareIn(serviceScope, SharingStarted.Eagerly, replay = 1)

	val metadataUpdateFlow: SharedFlow<SceneMetadataUpdate>
		get() = sceneMetadataRepository.metadataUpdateFlow

	fun getSceneSummaries(): SceneSummary = SceneSummary(
		sceneEditorRepository.getSceneTree(),
		sceneContentRepository.getDirtyBufferIds(),
	)

	fun subscribeToSceneUpdates(
		scope: CoroutineScope,
		onSceneListUpdate: (SceneSummary) -> Unit,
	): Job {
		val job = scope.launch {
			sceneListChannel.collect { summary ->
				withContext(dispatcherMain) {
					onSceneListUpdate(summary)
				}
			}
		}
		sceneEditorRepository.forceSceneListReload()
		return job
	}

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

		val scenePath = sceneEditorRepository.resolveSceneContentPath(sceneItem)
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

	/**
	 * The scene's live text (unsaved buffer first, then disk) with no caching and no
	 * writing-session baseline. Call off the main thread.
	 */
	fun getCurrentSceneContentOrNull(sceneItem: SceneItem): String? =
		runCatching { sceneContentRepository.getCurrentSceneContent(sceneItem) }
			.onFailure { Napier.w(it) { "Failed to read content for scene: ${sceneItem.id}" } }
			.getOrNull()

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
