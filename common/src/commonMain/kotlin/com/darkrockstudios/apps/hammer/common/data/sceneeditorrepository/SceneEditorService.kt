package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow

/**
 * The single Component-facing API for the scene-editing domain. Components and UI
 * talk only to this service (plus a few purpose-built UseCases such as
 * export/import); the underlying repositories are lower-level building blocks.
 *
 * The service owns write orchestration, derived scene-list state, and one-to-one
 * read delegations. Real logic and state live in the repositories — this is a
 * wide-but-thin door, not a god object.
 *
 * Phase A: every member delegates to the existing [SceneEditorRepository]. As the
 * monolith is carved into SceneRepository / SceneContentRepository /
 * SceneMetadataRepository, these delegations are repointed behind this stable API.
 */
class SceneEditorService(
	private val sceneEditorRepository: SceneEditorRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
) : ProjectScoped {

	override val projectScope: ProjectDefScope get() = sceneEditorRepository.projectScope

	val projectDef: ProjectDef get() = sceneEditorRepository.projectDef

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

	suspend fun storeSceneBuffer(sceneItem: SceneItem): Boolean =
		sceneEditorRepository.storeSceneBuffer(sceneItem)

	suspend fun storeAllBuffers() = sceneEditorRepository.storeAllBuffers()

	fun discardSceneBuffer(sceneDef: SceneItem) = sceneEditorRepository.discardSceneBuffer(sceneDef)

	fun onContentChanged(content: SceneContent, source: UpdateSource) =
		sceneEditorRepository.onContentChanged(content, source)

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
	): Job = sceneEditorRepository.subscribeToBufferUpdates(sceneDef, scope, onBufferUpdate)

	// endregion

	// region Delegated reads

	fun getSceneTree(): ImmutableTree<SceneItem> = sceneEditorRepository.getSceneTree()

	fun getSceneItemFromId(id: Int): SceneItem? = sceneEditorRepository.getSceneItemFromId(id)

	fun getSceneItemFromIdIncludingArchived(id: Int): SceneItem? =
		sceneEditorRepository.getSceneItemFromIdIncludingArchived(id)

	fun getArchivedScenes(): List<SceneItem> = sceneEditorRepository.getArchivedScenes()

	fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? =
		sceneEditorRepository.getSceneBuffer(sceneDef)

	fun loadSceneBuffer(sceneItem: SceneItem): SceneBuffer =
		sceneEditorRepository.loadSceneBuffer(sceneItem)

	suspend fun loadSceneBufferAsync(sceneItem: SceneItem): SceneBuffer =
		sceneEditorRepository.loadSceneBufferAsync(sceneItem)

	fun hasDirtyBuffers(): Boolean = sceneEditorRepository.hasDirtyBuffers()

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
}
