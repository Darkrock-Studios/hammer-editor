package com.darkrockstudios.apps.hammer.common.data.notes.note

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class NoteContent(
	val id: Long,
	val created: Instant,
	val content: String
)