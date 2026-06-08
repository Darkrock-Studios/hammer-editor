package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import kotlinx.coroutines.flow.first

/**
 * Rewrites confirmed/dismissed references in scene metadata when an entry id is remapped
 * (e.g. after a sync re-id). The scene-side implementation of [ReferenceRemapper].
 */
class SceneMetadataReferenceRemapper(
	private val sceneEditorRepository: SceneRepository,
	private val sceneMetadataDatasource: SceneMetadataDatasource,
	private val referenceIndexRepository: ReferenceIndexRepository,
) : ReferenceRemapper {
	override suspend fun remapEntryReferences(oldEntryId: Int, newEntryId: Int) {
		if (oldEntryId == newEntryId) return

		val sceneIds = collectAllSceneIds()
		var anyRewritten = false
		for (sceneId in sceneIds) {
			val metadata = sceneMetadataDatasource.loadMetadata(sceneId) ?: continue
			val rewritten = metadata.rewriteReferences(oldEntryId, newEntryId) ?: continue
			sceneMetadataDatasource.storeMetadata(rewritten, sceneId)
			anyRewritten = true
		}

		if (anyRewritten) referenceIndexRepository.markDirty()
	}

	private suspend fun collectAllSceneIds(): Set<Int> {
		val ids = mutableSetOf<Int>()
		val sceneTree = sceneEditorRepository.sceneTreeUpdates.first()
		sceneTree.root.forEach { node ->
			if (node.value.type == SceneItem.Type.Scene) ids.add(node.value.id)
		}
		sceneEditorRepository.getArchivedScenes().forEach { ids.add(it.id) }
		return ids
	}

	private fun SceneMetadata.rewriteReferences(oldId: Int, newId: Int): SceneMetadata? {
		val confirmedHasOld = oldId in confirmedReferences
		val dismissedHasOld = oldId in dismissedReferences
		if (!confirmedHasOld && !dismissedHasOld) return null

		return copy(
			confirmedReferences = if (confirmedHasOld) confirmedReferences - oldId + newId else confirmedReferences,
			dismissedReferences = if (dismissedHasOld) dismissedReferences - oldId + newId else dismissedReferences,
		)
	}
}
