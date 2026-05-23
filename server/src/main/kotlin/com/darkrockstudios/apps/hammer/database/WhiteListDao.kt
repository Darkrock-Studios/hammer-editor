package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetPaginatedWithAccountStatus
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
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

	open suspend fun addToWhiteList(email: String, dateAdded: Instant, reason: String): Unit = withContext(ioDispatcher) {
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

	open suspend fun getPaginated(limit: Long, offset: Long, sortOldestFirst: Boolean = false): List<WhiteList> =
		withContext(ioDispatcher) {
			return@withContext if (sortOldestFirst) {
				queries.getPaginatedOldestFirst(limit, offset).executeAsList()
			} else {
				queries.getPaginated(limit, offset).executeAsList()
			}
	}

	open suspend fun getPaginatedWithAccountStatus(
		limit: Long,
		offset: Long,
		sortOldestFirst: Boolean = false
	): List<GetPaginatedWithAccountStatus> =
		withContext(ioDispatcher) {
			return@withContext if (sortOldestFirst) {
				queries.getPaginatedWithAccountStatusOldestFirst(limit, offset).executeAsList().map {
					GetPaginatedWithAccountStatus(
						email = it.email,
						date_added = it.date_added,
						reason = it.reason,
						has_account = it.has_account
					)
				}
			} else {
				queries.getPaginatedWithAccountStatus(limit, offset).executeAsList()
			}
		}

	open suspend fun getByReason(reason: String): List<WhiteList> = withContext(ioDispatcher) {
		return@withContext queries.getByReason(reason).executeAsList()
	}

	open suspend fun countByReasonWithAccounts(reason: String): Long = withContext(ioDispatcher) {
		return@withContext queries.countByReasonWithAccounts(reason).executeAsOne()
	}

	open suspend fun updateReason(email: String, reason: String): Unit = withContext(ioDispatcher) {
		queries.updateReason(reason, email)
	}
}