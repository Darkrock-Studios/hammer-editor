package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.UserActivityDao
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
	 * Everything the Active Users dashboard needs, from a single 30-day fetch: the
	 * 24h/7d/30d distinct-user rollups (DAU/WAU/MAU) and the per-day trend series.
	 * The windows are subsets of the 30-day data, so they're derived in memory
	 * rather than as separate COUNT(DISTINCT) round-trips.
	 *
	 * Rows are fetched from the UTC-day start of the 30-day window so the leftmost
	 * trend day is complete; the rollups still use the precise [now]-relative
	 * cutoffs, so they're unaffected by that earlier fetch boundary.
	 */
	suspend fun activeUsersOverview(now: Instant): ActiveUsersOverview {
		val window30 = now - 30.days
		val window7 = now - 7.days
		val window24 = now - 24.hours
		val rows = userActivityDao.getActivitySince(truncateToUtcDay(window30))

		val sync = WindowAccumulator()
		val web = WindowAccumulator()
		val syncByDay = HashMap<Instant, MutableSet<Long>>()
		val webByDay = HashMap<Instant, MutableSet<Long>>()
		for (row in rows) {
			val (windows, byDay) = when (row.activity_type) {
				ActivityType.SYNC.dbValue -> sync to syncByDay
				ActivityType.WEB.dbValue -> web to webByDay
				else -> continue
			}
			byDay.getOrPut(truncateToUtcDay(row.hour_bucket)) { HashSet() }.add(row.user_id)
			windows.add(row.user_id, row.hour_bucket, window24, window7, window30)
		}

		val daily = ArrayList<DailyActiveUsers>()
		var day = truncateToUtcDay(window30)
		val lastDay = truncateToUtcDay(now)
		while (day <= lastDay) {
			daily += DailyActiveUsers(
				day = day,
				sync = (syncByDay[day]?.size ?: 0).toLong(),
				web = (webByDay[day]?.size ?: 0).toLong(),
			)
			day += 1.days
		}

		return ActiveUsersOverview(daily = daily, sync = sync.toCounts(), web = web.toCounts())
	}

	suspend fun purgeBefore(cutoff: Instant) {
		userActivityDao.deleteActivityBefore(cutoff)
	}
}

/** Accumulates distinct user ids into the three rolling windows in one pass. */
private class WindowAccumulator {
	private val h24 = HashSet<Long>()
	private val d7 = HashSet<Long>()
	private val d30 = HashSet<Long>()

	fun add(userId: Long, at: Instant, since24: Instant, since7: Instant, since30: Instant) {
		if (at >= since30) d30.add(userId)
		if (at >= since7) d7.add(userId)
		if (at >= since24) h24.add(userId)
	}

	fun toCounts() = WindowCounts(h24.size.toLong(), d7.size.toLong(), d30.size.toLong())
}

data class ActiveUsersOverview(
	val daily: List<DailyActiveUsers>,
	val sync: WindowCounts,
	val web: WindowCounts,
)

/** Distinct active users over the 24h / 7d / 30d rolling windows. */
data class WindowCounts(
	val h24: Long,
	val d7: Long,
	val d30: Long,
)

data class DailyActiveUsers(
	val day: Instant,
	val sync: Long,
	val web: Long,
)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Floor an instant to its UTC day start, matching the dashboard's daily bucketing. */
private fun truncateToUtcDay(instant: Instant): Instant =
	Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() / MILLIS_PER_DAY * MILLIS_PER_DAY)
