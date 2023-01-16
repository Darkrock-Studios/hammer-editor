package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable

@Serializable
data class EntryContainer(
	val entry: EntryContent
) {
	fun toDef(projectDef: ProjectDef): EntryDef = EntryDef(
		projectDef = projectDef,
		id = entry.id,
		type = entry.type,
		name = entry.name
	)
}