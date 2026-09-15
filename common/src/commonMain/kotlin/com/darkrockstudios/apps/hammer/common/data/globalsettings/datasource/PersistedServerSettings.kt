package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

/**
 * Tokenless persisted form of [ServerSettings] written to the per-workspace
 * `server.json`. The secret token fields live in [AuthTokenStore] instead.
 *
 * [ssl] defaults to true so a file written before plaintext was supported, which carries no `ssl`
 * key, restores as HTTPS.
 */
@Serializable
data class PersistedServerSettings(
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
	val ssl: Boolean = true,
)

fun ServerSettings.toPersisted(): PersistedServerSettings = PersistedServerSettings(
	url = url,
	email = email,
	userId = userId,
	ssl = ssl,
)

fun PersistedServerSettings.toServerSettings(tokens: AuthTokens?): ServerSettings = ServerSettings(
	ssl = ssl,
	url = url,
	email = email,
	userId = userId,
	bearerToken = tokens?.bearerToken,
	refreshToken = tokens?.refreshToken,
)
