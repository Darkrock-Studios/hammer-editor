package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class WhiteListDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.whiteListQueries

	suspend fun isWhiteListed(email: String): Boolean = withContext(ioDispatcher) {
		val query = queries.isWhiteListed(email)
		return@withContext query.executeAsOne()
	}

	suspend fun addToWhiteList(email: String) = withContext(ioDispatcher) {
		queries.addToWhiteList(email)
	}

	suspend fun removeFromWhiteList(email: String) = withContext(ioDispatcher) {
		queries.removeFromWhiteList(email)
	}

	suspend fun getAllWhiteListedEmails(): List<String> = withContext(ioDispatcher) {
		val query = queries.getAll()
		return@withContext query.executeAsList()
	}
}