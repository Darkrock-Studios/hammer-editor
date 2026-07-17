package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

/**
 * Tokenless persisted form of [ServerSettings] written to the per-workspace
 * `server.json`. The secret token fields live in [AuthTokenStore] instead.
 *
 * Note the absence of an `ssl` field: clients are HTTPS-only, so a legacy `ssl` key
 * in an existing `server.json` is ignored on load and every restored connection is HTTPS.
 */
@Serializable
data class PersistedServerSettings(
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
)

fun ServerSettings.toPersisted(): PersistedServerSettings = PersistedServerSettings(
	url = url,
	email = email,
	userId = userId,
)

fun PersistedServerSettings.toServerSettings(tokens: AuthTokens?): ServerSettings = ServerSettings(
	ssl = true,
	url = url,
	email = email,
	userId = userId,
	bearerToken = tokens?.bearerToken,
	refreshToken = tokens?.refreshToken,
)
