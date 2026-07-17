package com.darkrockstudios.apps.hammer.common.data.globalsettings

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

@Serializable
data class ServerSettings(
	// Clients always talk HTTPS; only test harnesses pointing at a plain-HTTP in-process
	// server construct this with ssl = false.
	val ssl: Boolean = true,
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
	@TomlLiteralString
	val bearerToken: String?,
	@TomlLiteralString
	val refreshToken: String?,
)