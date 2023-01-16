package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

@Serializable
data class EntryContent(
	val id: Int,
	val name: String,
	val type: EntryType,
	val text: String,
	val tags: List<String>
) {
	fun toDef(projectDef: ProjectDef): EntryDef = EntryDef(
		projectDef = projectDef,
		id = id,
		type = type,
		name = name
	)
}