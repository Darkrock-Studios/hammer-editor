package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

/**
 * Tokenless persisted form of [ServerSettings] written to the per-workspace
 * `server.json`. The secret token fields live in [AuthTokenStore] instead.
 */
@Serializable
data class PersistedServerSettings(
	val ssl: Boolean,
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
)

fun ServerSettings.toPersisted(): PersistedServerSettings = PersistedServerSettings(
	ssl = ssl,
	url = url,
	email = email,
	userId = userId,
)

fun PersistedServerSettings.toServerSettings(tokens: AuthTokens?): ServerSettings = ServerSettings(
	ssl = ssl,
	url = url,
	email = email,
	userId = userId,
	bearerToken = tokens?.bearerToken,
	refreshToken = tokens?.refreshToken,
)
