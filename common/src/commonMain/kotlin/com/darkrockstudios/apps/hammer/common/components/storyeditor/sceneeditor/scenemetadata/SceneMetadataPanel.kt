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
	fun confirmReference(entryId: Int)
	fun unconfirmReference(entryId: Int)
	fun dismissReference(entryId: Int)
	fun restoreDismissedReference(entryId: Int)
	fun navigateToEntry(entryDef: EntryDef)

	data class State(
		val sceneItem: SceneItem,
		val filename: String = "",
		val path: String = "",
		val wordCount: Int = 0,
		val metadata: SceneMetadata = SceneMetadata(),
		val confirmedRefs: List<EntryDef> = emptyList(),
		val dismissedRefs: List<EntryDef> = emptyList(),
		val suggestedRefs: List<SuggestedRef> = emptyList(),
	)

	data class SuggestedRef(val entryDef: EntryDef, val matchedAlias: String)
}