package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata

/**
 * Drops reference IDs whose encyclopedia entry no longer exists from a piece of
 * scene metadata, returning a cleaned copy.
 */
class ScrubInvalidReferencesUseCase(
	private val encyclopediaRepository: EncyclopediaRepository,
) {
	operator fun invoke(metadata: SceneMetadata): SceneMetadata {
		if (metadata.confirmedReferences.isEmpty() && metadata.dismissedReferences.isEmpty()) {
			return metadata
		}
		val newConfirmed = metadata.confirmedReferences
			.filterTo(HashSet()) { encyclopediaRepository.findEntryDef(it) != null }
		val newDismissed = metadata.dismissedReferences
			.filterTo(HashSet()) { encyclopediaRepository.findEntryDef(it) != null }
		return if (newConfirmed.size == metadata.confirmedReferences.size &&
			newDismissed.size == metadata.dismissedReferences.size
		) {
			metadata
		} else {
			metadata.copy(
				confirmedReferences = newConfirmed,
				dismissedReferences = newDismissed,
			)
		}
	}
}
