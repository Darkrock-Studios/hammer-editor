package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncryptedSharedPrefsAuthTokenStoreTest {

	private val disk = FakeEncryptedPrefsDisk()

	private fun createStore() = EncryptedSharedPrefsAuthTokenStore(
		json = Json,
		openPrefs = disk::open,
		deletePrefs = disk::delete,
	)

	@Test
	fun `tokens round trip`() {
		val store = createStore()

		store.put(URL, USER_ID, TOKENS)

		assertEquals(TOKENS, createStore().get(URL, USER_ID))
	}

	@Test
	fun `keyset the master key cannot decrypt is treated as no tokens`() {
		disk.keysetDecryptable = false
		val store = createStore()

		val tokens = store.get(URL, USER_ID)

		assertNull(tokens)
	}

	@Test
	fun `store is usable again after an undecryptable keyset`() {
		disk.keysetDecryptable = false
		createStore().get(URL, USER_ID)

		createStore().put(URL, USER_ID, TOKENS)

		assertEquals(TOKENS, createStore().get(URL, USER_ID))
	}

	@Test
	fun `value that cannot be decrypted is treated as no tokens`() {
		createStore().put(URL, USER_ID, TOKENS)
		disk.valuesDecryptable = false

		val tokens = createStore().get(URL, USER_ID)

		assertNull(tokens)
	}

	@Test
	fun `put replaces a value that cannot be decrypted`() {
		createStore().put(URL, USER_ID, TOKENS)
		disk.valuesDecryptable = false
		val replacement = AuthTokens(bearerToken = "new-bearer", refreshToken = "new-refresh")

		createStore().put(URL, USER_ID, replacement)

		assertEquals(replacement, createStore().get(URL, USER_ID))
	}

	companion object {
		private const val URL = "https://hammer.example"
		private const val USER_ID = 7L
		private val TOKENS = AuthTokens(bearerToken = "bearer", refreshToken = "refresh")
	}
}

/**
 * Models the encrypted prefs file: [open] fails like EncryptedSharedPreferences.create
 * when the Keystore master key cannot decrypt the stored keyset, and [delete] removes
 * the file (keyset included) so the next open generates a fresh one.
 */
private class FakeEncryptedPrefsDisk {
	val values = mutableMapOf<String, String>()
	var keysetDecryptable = true
	var valuesDecryptable = true

	fun open(): SharedPreferences {
		if (!keysetDecryptable) throw AEADBadTagException()
		return FakePrefs()
	}

	fun delete() {
		values.clear()
		keysetDecryptable = true
		valuesDecryptable = true
	}

	private inner class FakePrefs : SharedPreferences {
		override fun getString(key: String, defValue: String?): String? {
			val stored = values[key] ?: return defValue
			if (!valuesDecryptable) throw SecurityException("Could not decrypt value")
			return stored
		}

		override fun contains(key: String): Boolean = key in values
		override fun getAll(): MutableMap<String, *> = values.toMutableMap()
		override fun edit(): SharedPreferences.Editor = FakeEditor()

		override fun getStringSet(key: String, defValues: MutableSet<String>?) = unsupported()
		override fun getInt(key: String, defValue: Int): Int = unsupported()
		override fun getLong(key: String, defValue: Long): Long = unsupported()
		override fun getFloat(key: String, defValue: Float): Float = unsupported()
		override fun getBoolean(key: String, defValue: Boolean): Boolean = unsupported()
		override fun registerOnSharedPreferenceChangeListener(
			listener: SharedPreferences.OnSharedPreferenceChangeListener
		) = unsupported()

		override fun unregisterOnSharedPreferenceChangeListener(
			listener: SharedPreferences.OnSharedPreferenceChangeListener
		) = unsupported()
	}

	private inner class FakeEditor : SharedPreferences.Editor {
		private val pending = mutableMapOf<String, String?>()
		private var clearAll = false

		override fun putString(key: String, value: String?) = apply { pending[key] = value }
		override fun remove(key: String) = apply { pending[key] = null }
		override fun clear() = apply { clearAll = true }

		override fun commit(): Boolean {
			if (clearAll) values.clear()
			pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
			valuesDecryptable = true
			return true
		}

		override fun apply() {
			commit()
		}

		override fun putStringSet(key: String, values: MutableSet<String>?) = unsupported()
		override fun putInt(key: String, value: Int) = unsupported()
		override fun putLong(key: String, value: Long) = unsupported()
		override fun putFloat(key: String, value: Float) = unsupported()
		override fun putBoolean(key: String, value: Boolean) = unsupported()
	}
}

private fun unsupported(): Nothing = throw UnsupportedOperationException()
