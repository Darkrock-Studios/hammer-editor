package com.darkrockstudios.apps.hammer.admin

data class ServerConfigKey<T>(
	val key: String,
	val default: T,
	val parse: (String) -> T,
	val serialize: (T) -> String = { it.toString() }
) {
	companion object {
		fun string(key: String, default: String) = ServerConfigKey(key, default, { it }, { it })
		fun int(key: String, default: Int) = ServerConfigKey(key, default, { it.toInt() }, { it.toString() })
		fun boolean(key: String, default: Boolean) =
			ServerConfigKey(key, default, { it.toBoolean() }, { it.toString() })
	}
}

object AdminServerConfig {
	val WHITELIST_ENABLED = ServerConfigKey.boolean("whitelist_enabled", true)
	val SERVER_MESSAGE = ServerConfigKey.string("server_message", "Welcome to this instance of Hammer! Happy syncing!")
	val CONTACT_EMAIL = ServerConfigKey.string("contact_email", "")
	val DEFAULT_LOCALE = ServerConfigKey.string("default_locale", "en")
}
