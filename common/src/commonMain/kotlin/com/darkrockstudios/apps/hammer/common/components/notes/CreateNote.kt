package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import kotlinx.serialization.Serializable

interface CreateNote {
	val state: Value<State>
	val noteText: Value<String>

	@Serializable
	data class State(
		val confirmDiscard: Boolean = false,
	)

	suspend fun createNote(noteText: String): NoteError
	fun closeCreate()
	fun confirmDiscard()
	fun cancelDiscard()
	fun onTextChanged(newText: String)
	fun clearText()
}