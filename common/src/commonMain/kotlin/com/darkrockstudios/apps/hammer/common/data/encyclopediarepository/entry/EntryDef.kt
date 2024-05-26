package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

@Serializable
data class EntryDef(
	val projectDef: ProjectDef,
	val id: Int,
	val type: EntryType,
	val name: String
)