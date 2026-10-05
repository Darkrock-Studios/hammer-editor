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
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * [AuthTokenStore] backed by [EncryptedSharedPreferences] with an Android
 * Keystore-backed master key. The account-keyed token map is stored as JSON in a
 * single encrypted preferences entry.
 */
class EncryptedSharedPrefsAuthTokenStore internal constructor(
	private val json: Json,
	private val openPrefs: () -> SharedPreferences,
	private val resetPrefs: () -> Unit,
) : AuthTokenStore {

	constructor(context: Context, json: Json) : this(
		json = json,
		openPrefs = { openEncryptedPrefs(context) },
		resetPrefs = { resetEncryptedPrefs(context) },
	)

	private val lock = reentrantLock()

	private val prefs: SharedPreferences by lazy {
		try {
			openPrefs()
		} catch (e: GeneralSecurityException) {
			recreatePrefs(e)
		} catch (e: IOException) {
			recreatePrefs(e)
		}
	}

	// The keyset lives in the prefs file but its master key lives in the Keystore, which is
	// never backed up, so a restored or orphaned file is undecryptable, and some keystores
	// leave the master key itself unusable. Start over with both (forces re-login).
	private fun recreatePrefs(cause: Exception): SharedPreferences {
		Napier.w("Auth token keyset is unreadable; recreating the store", cause)
		resetPrefs()
		return openPrefs()
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
		return try {
			val stored = prefs.getString(TOKENS_KEY, null) ?: return emptyMap()
			json.decodeFromString<Map<String, AuthTokens>>(stored)
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w("Failed to read encrypted auth tokens; treating as empty", e)
			emptyMap()
		}
	}

	private fun storeMap(tokens: Map<String, AuthTokens>) {
		prefs.edit().putString(TOKENS_KEY, json.encodeToString(tokens)).apply()
	}

	companion object {
		private const val PREFS_NAME = "hammer_auth_tokens"
		private const val TOKENS_KEY = "tokens"
		private const val ANDROID_KEYSTORE = "AndroidKeyStore"

		private fun openEncryptedPrefs(context: Context): SharedPreferences {
			val masterKey = MasterKey.Builder(context)
				.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
				.build()

			return EncryptedSharedPreferences.create(
				context,
				PREFS_NAME,
				masterKey,
				EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
				EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
			)
		}

		private fun resetEncryptedPrefs(context: Context) {
			context.deleteSharedPreferences(PREFS_NAME)
			KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
				.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
		}
	}
}
