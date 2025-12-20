package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

open class ServerConfigDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.serverConfigQueries

	open suspend fun getConfig(key: String): String? = withContext(ioDispatcher) {
		queries.getConfig(key).executeAsOneOrNull()
	}

	open suspend fun upsertConfig(key: String, value: String): Unit = withContext(ioDispatcher) {
		queries.upsertConfig(key, value)
	}
}
