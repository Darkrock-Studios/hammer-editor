package com.darkrockstudios.apps.hammer.common.data.globalsettings

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

@Serializable
data class ServerSettings(
	// HTTPS unless the user typed an http:// URL for a self-hosted server that has no
	// certificate. See parseServerUrl.
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