package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import kotlinx.serialization.Serializable

interface CreateNote : TagSuggesting {
	val state: Value<State>
	val noteText: Value<String>

	@Serializable
	data class State(
		val confirmDiscard: Boolean = false,
	)

	suspend fun createNote(noteText: String, tags: Set<String>): NoteError
	fun closeCreate()
	fun confirmDiscard()
	fun cancelDiscard()
	fun onTextChanged(newText: String)
	fun clearText()
}