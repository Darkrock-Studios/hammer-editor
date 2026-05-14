package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository

/**
 * When an entry is created, renamed, or gets a new alias, walks the project's
 * scenes (live + archived) and adds the entry to the `confirmedReferences` of
 * each scene whose text matches its current name or aliases.
 *
 * Skips:
 * - Entries whose type is not in [ReferenceIndexConfig.enabledEntryTypes]
 *   (matched at the service layer via [ReferenceIndexService.findScenesMatchingEntry]
 *   - actually, not yet; the service treats names as the contract and the
 *   filter happens here so callers don't have to load config).
 * - Scenes that already confirm or dismiss the entry (sticky semantics).
 *
 * Composes [ScrubInvalidReferencesUseCase] on the way out so any orphan IDs in
 * the touched scenes' metadata get cleaned in the same write.
 */
class BackfillEntryReferencesUseCase(
	private val sceneEditor: SceneEditorRepository,
	private val referenceIndexService: ReferenceIndexService,
	private val scrubInvalidReferences: ScrubInvalidReferencesUseCase,
	private val config: ReferenceIndexConfig,
) {
	suspend operator fun invoke(entry: EntryContent) {
		if (entry.type !in config.enabledEntryTypes) return

		val names = listOf(entry.name) + entry.aliases
		val matchingSceneIds = referenceIndexService.findScenesMatchingEntry(entry.id, names)
		if (matchingSceneIds.isEmpty()) return

		for (sceneId in matchingSceneIds) {
			val metadata = sceneEditor.loadSceneMetadata(sceneId)
			// Defensive: findScenesMatchingEntry already excludes confirmed/dismissed,
			// but check again so a race doesn't double-add.
			if (entry.id in metadata.confirmedReferences) continue
			if (entry.id in metadata.dismissedReferences) continue
			val newMetadata = metadata.copy(
				confirmedReferences = metadata.confirmedReferences + entry.id,
			)
			sceneEditor.storeMetadata(scrubInvalidReferences(newMetadata), sceneId)
		}
	}
}
