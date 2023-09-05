package com.darkrockstudios.apps.hammer.common.components.notes

import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError

interface CreateNote {
	suspend fun createNote(noteText: String): NoteError
	fun closeCreate()
}