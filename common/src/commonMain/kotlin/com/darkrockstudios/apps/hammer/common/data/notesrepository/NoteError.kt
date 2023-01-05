package com.darkrockstudios.apps.hammer.common.data.notesrepository

enum class NoteError {
	NONE,
	EMPTY,
	TOO_LONG;

	val isSuccess: Boolean
		get() = this == NONE
}

class InvalidNote(val error: NoteError) : Exception("Note failed validation: $error")