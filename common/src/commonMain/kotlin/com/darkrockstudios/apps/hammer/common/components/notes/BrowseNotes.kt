package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import kotlinx.serialization.Serializable

interface BrowseNotes {
	val state: Value<State>

	fun viewNote(noteId: Int)
	fun showCreate()

	@Serializable
	data class State(
		val projectDef: ProjectDef,
		val notes: List<NoteContent>,
	)
}