package com.darkrockstudios.apps.hammer.common.data.secrets

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.aakira.napier.Napier
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * [SecretStore] backed by [EncryptedSharedPreferences], one entry per key, under a master key of its
 * own in the Android Keystore: starting over with it never touches the auth tokens' master key.
 */
class EncryptedSharedPrefsSecretStore internal constructor(
	private val openPrefs: () -> SharedPreferences,
	private val resetPrefs: () -> Unit,
) : SecretStore {

	constructor(context: Context) : this(
		openPrefs = { openEncryptedPrefs(context) },
		resetPrefs = { resetEncryptedPrefs(context) },
	)

	private val prefs: SharedPreferences by lazy {
		try {
			openPrefs()
		} catch (e: GeneralSecurityException) {
			recreatePrefs(e)
		} catch (e: IOException) {
			recreatePrefs(e)
		}
	}

	// A restored or orphaned keyset cannot be decrypted without its Keystore key, which is never backed up.
	private fun recreatePrefs(cause: Exception): SharedPreferences {
		Napier.w("Secret store keyset is unreadable; recreating it", cause)
		resetPrefs()
		return openPrefs()
	}

	override fun get(key: String): String? = try {
		prefs.getString(key, null)
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.w("Failed to read secret '$key'", e)
		null
	}

	override fun put(key: String, value: String) {
		val editor = prefs.edit()
		if (value.isEmpty()) editor.remove(key) else editor.putString(key, value)
		editor.apply()
	}

	override fun removeAll(prefix: String) {
		val editor = prefs.edit()
		prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
		editor.apply()
	}

	companion object {
		private const val PREFS_NAME = "hammer_secrets"
		private const val MASTER_KEY_ALIAS = "hammer_secrets_master_key"
		private const val ANDROID_KEYSTORE = "AndroidKeyStore"

		private fun openEncryptedPrefs(context: Context): SharedPreferences {
			val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
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
			KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
		}
	}
}
