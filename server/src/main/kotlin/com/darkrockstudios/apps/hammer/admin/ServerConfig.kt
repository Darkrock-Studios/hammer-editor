package com.darkrockstudios.apps.hammer.admin

data class ConfigKey(val key: String, val default: String)

object ServerConfig {
	val WHITELIST_ENABLED = ConfigKey("whitelist_enabled", "false")
}
