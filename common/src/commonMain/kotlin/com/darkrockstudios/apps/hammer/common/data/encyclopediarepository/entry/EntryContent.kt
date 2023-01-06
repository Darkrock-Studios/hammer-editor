package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import kotlinx.serialization.Serializable

@Serializable
data class EntryContent(
	val id: Long,
	val name: String,
	val type: EntryType,
	val text: String,
	val tags: List<String>
)