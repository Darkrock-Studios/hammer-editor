package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import org.koin.core.component.KoinComponent

class ConfigRepository(private val dao: ServerConfigDao) : KoinComponent {

	suspend fun getString(key: ConfigKey): String {
		return dao.getConfig(key.key) ?: key.default
	}

	suspend fun set(key: ConfigKey, value: String) {
		dao.upsertConfig(key.key, value)
	}

	suspend fun getInt(key: ConfigKey): Int = getString(key).toInt()

	suspend fun getBoolean(key: ConfigKey): Boolean = getString(key).toBoolean()
}
