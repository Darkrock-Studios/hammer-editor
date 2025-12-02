package com.darkrockstudios.apps.hammer.common.data.notesrepository.note

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString
import kotlin.time.Instant

@Serializable
data class NoteContent(
	val id: Int,
	val created: Instant,
	@TomlMultilineString
	val content: String
)