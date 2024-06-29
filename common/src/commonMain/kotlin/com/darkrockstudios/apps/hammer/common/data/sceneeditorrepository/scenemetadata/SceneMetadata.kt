package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlMultilineString

@Serializable
data class SceneMetadata(
	@TomlMultilineString
	val outline: String = "",
	@TomlMultilineString
	val notes: String = "",
)
