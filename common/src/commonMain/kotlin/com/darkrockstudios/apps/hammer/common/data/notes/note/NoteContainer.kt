package com.darkrockstudios.apps.hammer.common.data.notes.note

import kotlinx.serialization.Serializable

@Serializable
data class NoteContainer(
	val note: NoteContent
)