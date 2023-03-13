package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface Notes : HammerComponent {
	val state: Value<State>

	fun createNote(noteText: String): NoteError
	fun deleteNote(id: Int)
	fun updateNote(noteContent: NoteContent)

	fun confirmDelete(note: NoteContent)
	fun dismissConfirmDelete()

	fun showCreate()
	fun dismissCreate()

	data class State(
		val projectDef: ProjectDef,
		val notes: List<NoteContent>,
		val confirmDelete: NoteContent? = null,
		val showCreate: Boolean = false
	)
}