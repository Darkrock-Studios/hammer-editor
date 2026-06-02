package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

open class ErrorLogDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.errorLogQueries

	open suspend fun recordError(
		fingerprint: String,
		exceptionType: String,
		route: String?,
		userId: Long?,
		message: String?,
		stackTrace: String?,
		now: Instant,
	): Unit = withContext(ioDispatcher) {
		queries.recordError(fingerprint, exceptionType, route, userId, message, stackTrace, now, now)
	}

	open suspend fun getRecentErrors(limit: Long, offset: Long): List<Error_log> = withContext(ioDispatcher) {
		queries.getRecentErrors(limit, offset).executeAsList()
	}

	open suspend fun getErrorCount(): Long = withContext(ioDispatcher) {
		queries.getErrorCount().executeAsOne()
	}

	open suspend fun deleteErrorsBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.deleteErrorsBefore(cutoff)
	}
}
