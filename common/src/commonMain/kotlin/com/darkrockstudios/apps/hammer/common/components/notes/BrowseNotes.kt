package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import kotlinx.serialization.Serializable

interface BrowseNotes {
	val state: Value<State>
	val rankedTags: Value<List<TagCount>>

	/** Note ids in the order the list shows them; [State.notes] order until the list reports one. */
	val visibleOrder: List<Int>

	fun viewNote(noteId: Int)
	fun showCreate()

	/** The list filters and sorts in the UI, so it reports the order it shows here. */
	fun onVisibleOrderChanged(noteIds: List<Int>)

	@Serializable
	data class State(
		val projectDef: ProjectDef,
		val notes: List<NoteContent>,
	)
}