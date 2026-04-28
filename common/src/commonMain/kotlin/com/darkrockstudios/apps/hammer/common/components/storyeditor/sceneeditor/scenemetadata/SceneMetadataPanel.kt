package com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface SceneMetadataPanel : HammerComponent {
	val state: Value<State>

	fun updateOutline(text: String)
	fun updateNotes(text: String)
	fun updateDraftName(text: String)
	fun validateDraftName(text: String): Boolean
	fun dismissReference(entryId: Int)
	fun restoreDismissedReference(entryId: Int)
	fun navigateToEntry(entryDef: EntryDef)

	/**
	 * Adds an entry to this scene's `confirmedReferences`. If the entry was
	 * dismissed it's removed from `dismissedReferences` in the same write so
	 * the user's pick wins over the prior dismissal.
	 */
	fun addConfirmedReference(entryId: Int)

	/**
	 * Search entries by name or alias for the manual "add reference" affordance.
	 * Case-insensitive substring match across all entry types. Already-confirmed
	 * entries are excluded; dismissed entries are included with [AddSuggestion.isDismissed]
	 * flagged so the UI can hint that picking will un-dismiss.
	 *
	 * Returns up to [maxResults] entries sorted by name.
	 */
	fun searchEntriesForAdd(query: String, maxResults: Int = 20): List<AddSuggestion>

	data class State(
		val sceneItem: SceneItem,
		val filename: String = "",
		val path: String = "",
		val wordCount: Int = 0,
		val metadata: SceneMetadata = SceneMetadata(),
		val confirmedRefs: List<EntryDef> = emptyList(),
		val dismissedRefs: List<EntryDef> = emptyList(),
	)

	data class AddSuggestion(
		val entryDef: EntryDef,
		val isDismissed: Boolean,
	)
}