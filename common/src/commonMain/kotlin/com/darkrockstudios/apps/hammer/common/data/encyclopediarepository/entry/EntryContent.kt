package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString

@Serializable
data class EntryContent(
	val id: Int,
	val name: String,
	val type: EntryType,
	@TomlMultilineString
	val text: String,
	val tags: Set<String>
) {
	fun toDef(projectDef: ProjectDef): EntryDef = EntryDef(
		projectDef = projectDef,
		id = id,
		type = type,
		name = name
	)
}