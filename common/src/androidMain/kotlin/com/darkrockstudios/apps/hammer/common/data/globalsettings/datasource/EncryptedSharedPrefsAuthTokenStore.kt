package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore.Companion.accountKey
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.json.Json

/**
 * [AuthTokenStore] backed by [EncryptedSharedPreferences] with an Android
 * Keystore-backed master key. The account-keyed token map is stored as JSON in a
 * single encrypted preferences entry.
 */
class EncryptedSharedPrefsAuthTokenStore(
	context: Context,
	private val json: Json,
) : AuthTokenStore {

	private val lock = reentrantLock()

	private val prefs: SharedPreferences by lazy {
		val masterKey = MasterKey.Builder(context)
			.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
			.build()

		EncryptedSharedPreferences.create(
			context,
			PREFS_NAME,
			masterKey,
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
		)
	}

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
		val stored = prefs.getString(TOKENS_KEY, null) ?: return emptyMap()
		return try {
			json.decodeFromString<Map<String, AuthTokens>>(stored)
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to parse encrypted auth tokens; treating as empty", e)
			emptyMap()
		}
	}

	private fun storeMap(tokens: Map<String, AuthTokens>) {
		prefs.edit().putString(TOKENS_KEY, json.encodeToString(tokens)).apply()
	}

	companion object {
		private const val PREFS_NAME = "hammer_auth_tokens"
		private const val TOKENS_KEY = "tokens"
	}
}
