package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository

/**
 * Walks the scenes that the inverted reference index reports as confirming a
 * given entry, removes the entry's id from each scene's `confirmedReferences`
 * AND `dismissedReferences`, and rewrites the metadata. Intended to be called
 * just before (or just after) an entry is deleted from the encyclopedia, so
 * the user-visible state is clean immediately rather than relying on the
 * lazy write-time scrub to catch up.
 *
 * Trade-off accepted: the inverted index only tracks **confirmed**
 * references, so scenes that have the deleted entry only in
 * `dismissedReferences` (never confirmed) are not visited eagerly. They heal
 * lazily via [ScrubInvalidReferencesUseCase] at the next save. Visiting them
 * would require either walking every scene (the expensive option this UseCase
 * was built to avoid) or doubling the cache footprint to track dismissals.
 *
 * Each affected scene gets exactly one `storeMetadata` call. The per-scene
 * cache delta naturally drains the deleted entry's key from the inverted map
 * as we go - the explicit `markEntryDeleted` at the call site is
 * belt-and-suspenders for cases where this UseCase's snapshot was empty
 * (e.g. dirty cache).
 */
class CleanupReferencesOnEntryDeleteUseCase(
	private val sceneEditor: SceneEditorRepository,
	private val referenceIndexService: ReferenceIndexService,
	private val scrubInvalidReferences: ScrubInvalidReferencesUseCase,
) {
	suspend operator fun invoke(deletedEntryId: Int) {
		val sceneIds = referenceIndexService.getScenesReferencing(deletedEntryId)
		for (sceneId in sceneIds) {
			val metadata = sceneEditor.loadSceneMetadata(sceneId)
			val newConfirmed = metadata.confirmedReferences - deletedEntryId
			val newDismissed = metadata.dismissedReferences - deletedEntryId
			if (newConfirmed.size == metadata.confirmedReferences.size &&
				newDismissed.size == metadata.dismissedReferences.size
			) {
				continue
			}
			val newMetadata = metadata.copy(
				confirmedReferences = newConfirmed,
				dismissedReferences = newDismissed,
			)
			sceneEditor.storeMetadata(scrubInvalidReferences(newMetadata), sceneId)
		}
	}
}
