package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent

interface ViewNote {
	val state: Value<State>
	val noteText: Value<String>

	data class State(
		val projectDef: ProjectDef,
		val note: NoteContent? = null,
		val confirmDelete: Boolean = false,
		val isEditing: Boolean = false
	)

	fun discardEdit()
	fun onContentChanged(newContent: String)
	suspend fun deleteNote(id: Int)
	fun confirmDelete()
	fun dismissConfirmDelete()
	suspend fun storeNoteUpdate()
	fun closeNote()
	fun beginEdit()
	fun isEditingAndDirty(): Boolean
}