package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
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
	val tags: Set<String>,
	val aliases: List<String> = emptyList(),
	/** Keeps this entry's name and aliases out of the spell-check session dictionary. */
	val excludeFromDictionary: Boolean = false,
	/** Image entity ids in display order; the first is the primary image. */
	val images: List<Int> = emptyList(),
) {
	fun toDef(projectDef: ProjectDef): EntryDef = EntryDef(
		projectDef = projectDef,
		id = id,
		type = type,
		name = name
	)

	/** The sync entity for this entry; its [ApiProjectEntity.hash] is the entry's sync hash. */
	fun toApiEntity(): ApiProjectEntity.EncyclopediaEntryEntity = ApiProjectEntity.EncyclopediaEntryEntity(
		id = id,
		name = name,
		entryType = type.text,
		text = text,
		tags = tags,
		aliases = aliases,
		excludeFromDictionary = excludeFromDictionary,
		images = images,
	)
}