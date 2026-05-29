package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.default_draft_name
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Owns scene metadata (per-scene outline/notes/draft/references/timestamps) and the project
 * metadata (responsibilities E + F of the old SceneEditorRepository).
 *
 * This is a *data* building block: pure persistence plus the two metadata flows. Higher-level
 * orchestration — marking the scene for sync and applying the reference-index delta on a
 * confirmed-references change — lives in [SceneEditorService.storeMetadata], not here.
 */
class SceneMetadataRepository(
	private val projectDef: ProjectDef,
	private val sceneMetadataDatasource: SceneMetadataDatasource,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val strRes: StrRes,
	private val clock: Clock,
) {

	private val projectMetadata = MutableSharedFlow<ProjectMetadata>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)

	private val _metadataUpdateFlow = MutableSharedFlow<Pair<Int, SceneMetadata>>(
		extraBufferCapacity = 8,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val metadataUpdateFlow: SharedFlow<Pair<Int, SceneMetadata>> = _metadataUpdateFlow

	/** Loads and caches the project metadata. Must be called once during project-scope init. */
	suspend fun initialize() {
		val loaded = projectMetadataDatasource.loadMetadata(projectDef)
		projectMetadata.emit(loaded)
	}

	suspend fun getMetadata(): ProjectMetadata = projectMetadata.first()

	/** Raw persisted metadata (nullable, no default-name fill). For delta/hash computation. */
	suspend fun loadRawMetadata(sceneId: Int): SceneMetadata? =
		sceneMetadataDatasource.loadMetadata(sceneId)

	suspend fun loadSceneMetadata(sceneId: Int): SceneMetadata {
		val defaultName: String = strRes.get(Res.string.default_draft_name)
		val metadata = sceneMetadataDatasource.loadMetadata(sceneId)
			?: SceneMetadata(currentDraftName = defaultName)
		return if (metadata.currentDraftName.isBlank()) {
			metadata.copy(currentDraftName = defaultName)
		} else {
			metadata
		}
	}

	/**
	 * Pure persist + emit. Sync-marking and reference-index delta are the caller's concern
	 * (see [SceneEditorService.storeMetadata]).
	 */
	suspend fun storeMetadata(metadata: SceneMetadata, sceneId: Int) {
		sceneMetadataDatasource.storeMetadata(metadata, sceneId)
		_metadataUpdateFlow.tryEmit(sceneId to metadata)
	}

	/** Stamp a scene's last-edited timestamp (backfilling `created` for older scenes). */
	suspend fun recordSceneActivity(sceneId: Int) {
		val now = clock.now()
		val existing = sceneMetadataDatasource.loadMetadata(sceneId)
		// Backfill `created` for scenes authored before SceneMetadata had the field.
		val createdFallback = existing?.created ?: getMetadata().info.created
		val updated = (existing ?: SceneMetadata()).copy(
			created = createdFallback,
			lastEdited = now,
		)
		sceneMetadataDatasource.storeMetadata(updated, sceneId)
		// Refresh open editors so their snapshot has the new timestamps.
		_metadataUpdateFlow.tryEmit(sceneId to updated)
	}
}
