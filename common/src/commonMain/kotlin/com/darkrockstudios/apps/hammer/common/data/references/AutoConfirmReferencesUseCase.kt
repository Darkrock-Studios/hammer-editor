package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import io.github.aakira.napier.Napier

/**
 * Runs the reference matcher against a scene's current buffer text and adds any
 * new hits to the scene's `confirmedReferences`. Intended to be called as part
 * of an explicit save action - typically just before the buffer is flushed to
 * disk so that the resulting metadata write captures the new references.
 *
 * Sticky semantics:
 * - Already-confirmed entries stay confirmed (matcher excludes them).
 * - Already-dismissed entries stay dismissed (matcher excludes them).
 * - Confirmation is additive only; this never removes a confirmed reference,
 *   even if the name has since been deleted from the text.
 *
 * Composes [ScrubInvalidReferencesUseCase] on the way out: a scene that already
 * carried orphan IDs will get them cleaned in the same write that adds the new
 * confirmations, so we don't accumulate stale data.
 */
class AutoConfirmReferencesUseCase(
	private val sceneEditor: SceneEditorService,
	private val referenceIndexService: ReferenceIndexService,
	private val scrubInvalidReferences: ScrubInvalidReferencesUseCase,
) {
	suspend operator fun invoke(sceneItem: SceneItem) {
		val buffer = sceneEditor.getSceneBuffer(sceneItem) ?: return
		val text = buffer.content.coerceMarkdown()
		val metadata = sceneEditor.loadSceneMetadata(sceneItem.id)

		val newRefs = referenceIndexService.computeAutoReferencesForScene(
			sceneId = sceneItem.id,
			sceneText = text,
			metadata = metadata,
		)
		if (newRefs.isEmpty()) return

		Napier.i(
			"AutoConfirm: scene ${sceneItem.id} adding ${newRefs.size} new reference(s): " +
				newRefs.joinToString(", ") { "${it.entryId} via '${it.matchedAlias}'" }
		)
		val newConfirmed = metadata.confirmedReferences + newRefs.map { it.entryId }
		val newMetadata = metadata.copy(confirmedReferences = newConfirmed)
		sceneEditor.storeMetadata(scrubInvalidReferences(newMetadata), sceneItem.id)
	}
}
