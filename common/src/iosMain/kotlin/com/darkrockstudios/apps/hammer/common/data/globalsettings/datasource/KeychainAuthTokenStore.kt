package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore.Companion.accountKey
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json

/**
 * iOS [AuthTokenStore] backed by the Keychain. The account-keyed token map is
 * serialized to JSON and stored as a single `kSecClassGenericPassword` item, so
 * the tokens are encrypted at rest by the OS (Secure Enclave / device passcode)
 * rather than sitting in a plaintext file in the app sandbox.
 */
@OptIn(ExperimentalSettingsImplementation::class)
class KeychainAuthTokenStore(
	private val json: Json,
	private val keychain: Settings = KeychainSettings(service = KEYCHAIN_SERVICE),
) : AuthTokenStore {

	private val lock = reentrantLock()

	override fun get(url: String, userId: Long): AuthTokens? = lock.withLock {
		loadMap()[accountKey(url, userId)]
	}

	override fun put(url: String, userId: Long, tokens: AuthTokens): Unit = lock.withLock {
		val updated = loadMap().toMutableMap()
		updated[accountKey(url, userId)] = tokens
		storeMap(updated)
	}

	override fun remove(url: String, userId: Long): Unit = lock.withLock {
		val updated = loadMap().toMutableMap()
		if (updated.remove(accountKey(url, userId)) != null) {
			storeMap(updated)
		}
	}

	private fun loadMap(): Map<String, AuthTokens> {
		val stored = keychain.getStringOrNull(TOKENS_KEY) ?: return emptyMap()
		return try {
			json.decodeFromString<Map<String, AuthTokens>>(stored)
			// Corruption or tampering: treat as no tokens (forces re-login).
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to parse Keychain auth tokens; treating as empty", e)
			emptyMap()
		}
	}

	private fun storeMap(tokens: Map<String, AuthTokens>) {
		keychain.putString(TOKENS_KEY, json.encodeToString(tokens))
	}

	companion object {
		private const val KEYCHAIN_SERVICE = "com.darkrockstudios.apps.hammer.auth_tokens"
		private const val TOKENS_KEY = "tokens"
	}
}
