package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

/**
 * Stores deduplicated server-side errors. Recording the same fingerprint again
 * bumps the occurrence count and last-seen rather than inserting a new row.
 */
class ErrorRepository(
	private val errorLogDao: ErrorLogDao,
	private val clock: Clock,
) : KoinComponent {

	suspend fun record(
		exceptionType: String,
		route: String?,
		userId: Long?,
		message: String?,
		stackTrace: String?,
	) {
		val fingerprint = fingerprint(exceptionType, route, userId)
		errorLogDao.recordError(
			fingerprint = fingerprint,
			exceptionType = exceptionType,
			route = route,
			userId = userId,
			message = message,
			stackTrace = stackTrace,
			now = clock.now(),
		)
	}

	suspend fun getRecent(page: Int, pageSize: Int, route: String? = null): List<Error_log> =
		if (route == null) {
			errorLogDao.getRecentErrors(pageSize.toLong(), (page.toLong() * pageSize))
		} else {
			errorLogDao.getRecentErrorsByRoute(route, pageSize.toLong(), (page.toLong() * pageSize))
		}

	suspend fun getCount(route: String? = null): Long =
		if (route == null) errorLogDao.getErrorCount() else errorLogDao.getErrorCountByRoute(route)

	/** Noisy error groups (>= [minOccurrences], last seen since [since]) not yet alerted on. */
	suspend fun errorsToAlert(minOccurrences: Int, since: Instant): List<Error_log> =
		errorLogDao.getErrorsToAlert(minOccurrences.toLong(), since)

	suspend fun markNotified(id: Long) = errorLogDao.markNotified(clock.now(), id)

	suspend fun purgeBefore(cutoff: Instant) = errorLogDao.deleteErrorsBefore(cutoff)

	private fun fingerprint(exceptionType: String, route: String?, userId: Long?): String =
		"$exceptionType|${route ?: ""}|${userId ?: ""}"
}
