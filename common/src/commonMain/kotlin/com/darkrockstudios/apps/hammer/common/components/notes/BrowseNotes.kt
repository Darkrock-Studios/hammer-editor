package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent

interface BrowseNotes {
	val state: Value<State>

	fun viewNote(noteId: Int)
	fun showCreate()

	@Parcelize
	data class State(
		val projectDef: ProjectDef,
		val notes: List<NoteContent>,
	) : Parcelable
}