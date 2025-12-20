package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import org.koin.core.component.KoinComponent

class ConfigRepository(private val dao: ServerConfigDao) : KoinComponent {

	suspend fun <T> get(key: ServerConfigKey<T>): T {
		return dao.getConfig(key.key)?.let { value ->
			runCatching { key.parse(value) }.getOrNull()
		} ?: key.default
	}

	suspend fun <T> set(key: ServerConfigKey<T>, value: T) {
		dao.upsertConfig(key.key, key.serialize(value))
	}
}
