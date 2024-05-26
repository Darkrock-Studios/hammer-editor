package com.darkrockstudios.apps.hammer.common.data.notesrepository.note

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString

@Serializable
data class NoteContent(
	val id: Int,
	val created: Instant,
	@TomlMultilineString
	val content: String
)