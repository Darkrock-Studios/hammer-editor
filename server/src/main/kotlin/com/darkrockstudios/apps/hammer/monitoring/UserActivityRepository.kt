package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.UserActivityDao
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Read/write access to deduplicated user-activity rows backing the "Active
 * Users" dashboard metric. The in-memory [UserActivityCollector] flushes keys
 * through here; the maintenance job purges past the retention window.
 */
class UserActivityRepository(
	private val userActivityDao: UserActivityDao,
) : KoinComponent {

	suspend fun recordKeys(keys: List<ActivityKey>) {
		for (key in keys) {
			userActivityDao.recordActivity(key.userId, key.type.dbValue, key.hourBucket)
		}
	}

	suspend fun uniqueUsers(type: ActivityType, since: Instant): Long =
		userActivityDao.countDistinctUsersSince(type.dbValue, since)

	/**
	 * Distinct active users per UTC day across [since]..[until], one point per day
	 * (gaps filled with zero) so the trend line is continuous. Daily granularity
	 * keeps each user counted once per day they were active — the standard DAU view.
	 */
	suspend fun dailyActiveUsers(since: Instant, until: Instant): List<DailyActiveUsers> {
		val rows = userActivityDao.getActivitySince(since)
		val syncByDay = HashMap<Instant, MutableSet<Long>>()
		val webByDay = HashMap<Instant, MutableSet<Long>>()
		for (row in rows) {
			val day = truncateToUtcDay(row.hour_bucket)
			when (row.activity_type) {
				ActivityType.SYNC.dbValue -> syncByDay.getOrPut(day) { HashSet() }.add(row.user_id)
				ActivityType.WEB.dbValue -> webByDay.getOrPut(day) { HashSet() }.add(row.user_id)
			}
		}

		val out = ArrayList<DailyActiveUsers>()
		var day = truncateToUtcDay(since)
		val lastDay = truncateToUtcDay(until)
		while (day <= lastDay) {
			out += DailyActiveUsers(
				day = day,
				sync = (syncByDay[day]?.size ?: 0).toLong(),
				web = (webByDay[day]?.size ?: 0).toLong(),
			)
			day += 1.days
		}
		return out
	}

	suspend fun purgeBefore(cutoff: Instant) {
		userActivityDao.deleteActivityBefore(cutoff)
	}
}

data class DailyActiveUsers(
	val day: Instant,
	val sync: Long,
	val web: Long,
)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Floor an instant to its UTC day start, matching the dashboard's daily bucketing. */
private fun truncateToUtcDay(instant: Instant): Instant =
	Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() / MILLIS_PER_DAY * MILLIS_PER_DAY)
