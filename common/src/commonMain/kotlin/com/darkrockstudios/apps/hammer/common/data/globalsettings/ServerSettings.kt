package com.darkrockstudios.apps.hammer.common.data.globalsettings

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

@Serializable
data class ServerSettings(
	val ssl: Boolean,
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
	@TomlLiteralString
	val installId: String,
	@TomlLiteralString
	val bearerToken: String?,
	@TomlLiteralString
	val refreshToken: String?,
)