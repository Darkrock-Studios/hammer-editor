package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

/**
 * Tokenless persisted form of [ServerSettings] written to the per-workspace
 * `server.json`. The secret token fields live in [AuthTokenStore] instead.
 *
 * Thar be dragons: the scheme is stored as [plaintext], never under an `ssl` key. Files from before
 * clients went HTTPS-only can still hold `ssl: false`, which was never chosen through the http://
 * opt-in, so reading `ssl` would silently downgrade them to cleartext. Only a file written after
 * that opt-in existed can carry `plaintext: true`.
 */
@Serializable
data class PersistedServerSettings(
	@TomlLiteralString
	val url: String,
	@TomlLiteralString
	val email: String,
	val userId: Long,
	val plaintext: Boolean = false,
)

fun ServerSettings.toPersisted(): PersistedServerSettings = PersistedServerSettings(
	url = url,
	email = email,
	userId = userId,
	plaintext = !ssl,
)

fun PersistedServerSettings.toServerSettings(tokens: AuthTokens?): ServerSettings = ServerSettings(
	ssl = !plaintext,
	url = url,
	email = email,
	userId = userId,
	bearerToken = tokens?.bearerToken,
	refreshToken = tokens?.refreshToken,
)
