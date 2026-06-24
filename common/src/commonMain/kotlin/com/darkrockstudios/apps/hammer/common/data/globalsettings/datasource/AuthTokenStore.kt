package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import kotlinx.serialization.Serializable

/**
 * Auth tokens for a single account. Both fields are nullable: an account can be
 * configured before a session exists.
 */
@Serializable
data class AuthTokens(
	val bearerToken: String?,
	val refreshToken: String?,
)

/**
 * Per-machine storage for sync auth tokens, keyed by account (server url + userId).
 *
 * Tokens live here, in app-private storage, rather than in the per-workspace
 * `server.json`, so they do not travel when a workspace folder is moved or shared.
 */
interface AuthTokenStore {
	fun get(url: String, userId: Long): AuthTokens?
	fun put(url: String, userId: Long, tokens: AuthTokens)
	fun remove(url: String, userId: Long)

	companion object {
		fun accountKey(url: String, userId: Long): String = "$url|$userId"
	}
}
