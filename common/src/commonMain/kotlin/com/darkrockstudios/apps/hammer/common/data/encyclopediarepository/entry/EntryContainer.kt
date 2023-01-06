package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import kotlinx.serialization.Serializable

@Serializable
data class EntryContainer(
	val entry: EntryContent
)