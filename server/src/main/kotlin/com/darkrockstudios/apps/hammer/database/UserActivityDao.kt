package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.User_activity
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

open class UserActivityDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.userActivityQueries

	open suspend fun recordActivity(
		userId: Long,
		activityType: String,
		hourBucket: Instant,
	): Unit = withContext(ioDispatcher) {
		queries.recordActivity(userId, activityType, hourBucket)
	}

	open suspend fun countDistinctUsersSince(activityType: String, since: Instant): Long =
		withContext(ioDispatcher) {
			queries.countDistinctUsersSince(activityType, since).executeAsOne()
		}

	open suspend fun getActivitySince(since: Instant): List<User_activity> =
		withContext(ioDispatcher) {
			queries.getActivitySince(since).executeAsList()
		}

	open suspend fun deleteActivityBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.deleteActivityBefore(cutoff)
	}
}
