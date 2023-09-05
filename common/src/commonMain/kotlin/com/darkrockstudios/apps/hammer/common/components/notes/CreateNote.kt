package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError

interface CreateNote {
	val state: Value<State>
	val noteText: Value<String>

	@Parcelize
	data class State(
		val confirmDiscard: Boolean = false,
	) : Parcelable

	suspend fun createNote(noteText: String): NoteError
	fun closeCreate()
	fun confirmDiscard()
	fun cancelDiscard()
	fun onTextChanged(newText: String)
	fun clearText()
}