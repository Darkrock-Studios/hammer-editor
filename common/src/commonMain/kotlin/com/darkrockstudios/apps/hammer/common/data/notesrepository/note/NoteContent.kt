package com.darkrockstudios.apps.hammer.common.data.notesrepository.note

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class NoteContent(
	val id: Int,
	val created: Instant,
	val content: String
)