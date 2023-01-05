package com.darkrockstudios.apps.hammer.common.data.notesrepository.note

import kotlinx.serialization.Serializable

@Serializable
data class NoteContainer(
	val note: NoteContent
)