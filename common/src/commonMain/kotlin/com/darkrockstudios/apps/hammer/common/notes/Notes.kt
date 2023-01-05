package com.darkrockstudios.apps.hammer.common.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface Notes : HammerComponent {
	val state: Value<State>

	fun createNote(noteText: String): NoteError
	fun deleteNote(id: Long)
	fun updateNote(noteContent: NoteContent)

	data class State(
		val projectDef: ProjectDef,
		val notes: List<NoteContent>
	)
}