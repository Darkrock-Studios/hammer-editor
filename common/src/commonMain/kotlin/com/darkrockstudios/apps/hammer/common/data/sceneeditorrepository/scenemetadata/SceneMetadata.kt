package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString
import kotlin.time.Instant

@Serializable
data class SceneMetadata(
	@TomlMultilineString
	val outline: String = "",
	@TomlMultilineString
	val notes: String = "",
	val currentDraftName: String = "",
	val confirmedReferences: Set<Int> = emptySet(),
	val dismissedReferences: Set<Int> = emptySet(),
	val tags: Set<String> = emptySet(),
	val created: Instant? = null,
	val lastEdited: Instant? = null,
)
