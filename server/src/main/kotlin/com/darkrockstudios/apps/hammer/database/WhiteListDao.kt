package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

open class WhiteListDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.whiteListQueries

	open suspend fun isWhiteListed(email: String): Boolean = withContext(ioDispatcher) {
		val query = queries.isWhiteListed(email)
		return@withContext query.executeAsOne()
	}

	open suspend fun addToWhiteList(email: String, dateAdded: Long, reason: String): Unit = withContext(ioDispatcher) {
		queries.addToWhiteList(email, dateAdded, reason)
	}

	open suspend fun removeFromWhiteList(email: String): Unit = withContext(ioDispatcher) {
		queries.removeFromWhiteList(email)
	}

	open suspend fun getAllWhiteListedEmails(): List<String> = withContext(ioDispatcher) {
		val query = queries.getAll()
		return@withContext query.executeAsList().map { it.email }
	}

	open suspend fun getAll(): List<WhiteList> = withContext(ioDispatcher) {
		return@withContext queries.getAll().executeAsList()
	}

	open suspend fun getWhiteListCount(): Long = withContext(ioDispatcher) {
		return@withContext queries.getCount().executeAsOne()
	}

	open suspend fun getWhiteListPaginated(limit: Long, offset: Long): List<String> = withContext(ioDispatcher) {
		return@withContext queries.getPaginated(limit, offset).executeAsList().map { it.email }
	}

	open suspend fun getPaginated(limit: Long, offset: Long): List<WhiteList> = withContext(ioDispatcher) {
		return@withContext queries.getPaginated(limit, offset).executeAsList()
	}

	open suspend fun getByReason(reason: String): List<WhiteList> = withContext(ioDispatcher) {
		return@withContext queries.getByReason(reason).executeAsList()
	}

	open suspend fun countByReasonWithAccounts(reason: String): Long = withContext(ioDispatcher) {
		return@withContext queries.countByReasonWithAccounts(reason).executeAsOne()
	}
}