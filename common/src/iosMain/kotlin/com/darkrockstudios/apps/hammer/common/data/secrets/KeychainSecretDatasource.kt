package com.darkrockstudios.apps.hammer.common.data.secrets

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

/** [SecretDatasource] backed by the Keychain, one `kSecClassGenericPassword` item per key. */
@OptIn(ExperimentalSettingsImplementation::class)
class KeychainSecretDatasource(
	private val keychain: Settings = KeychainSettings(service = KEYCHAIN_SERVICE),
) : SecretDatasource {

	override fun get(key: String): String? = keychain.getStringOrNull(key)

	override fun put(key: String, value: String) {
		if (value.isEmpty()) keychain.remove(key) else keychain.putString(key, value)
	}

	override fun removeAll(prefix: String) {
		keychain.keys.filter { it.startsWith(prefix) }.forEach(keychain::remove)
	}

	companion object {
		private const val KEYCHAIN_SERVICE = "com.darkrockstudios.apps.hammer.secrets"
	}
}
