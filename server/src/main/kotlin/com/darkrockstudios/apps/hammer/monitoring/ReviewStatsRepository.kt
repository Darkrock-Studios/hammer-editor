package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ReviewLifecycle
import com.darkrockstudios.apps.hammer.database.ReviewRequestDao
import com.darkrockstudios.apps.hammer.database.ReviewStatusCounts
import com.darkrockstudios.apps.hammer.utilities.truncateToUtcDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Admin usage stats for the "request an editor" feature, read straight from the
 * review requests themselves: requests are never purged, so no separate metric
 * table is needed.
 */
class ReviewStatsRepository(
	private val reviewRequestDao: ReviewRequestDao,
) {

	suspend fun overview(now: Instant): ReviewStats {
		val firstDay = (now - 30.days).truncateToUtcDay()
		val recent = reviewRequestDao.lifecycleSince(firstDay)

		return ReviewStats(
			requested = windowCounts(now, recent) { it.created },
			returned = windowCounts(now, recent) { it.submittedAt },
			resolved = windowCounts(now, recent) { it.resolvedAt },
			status = reviewRequestDao.statusCounts(now),
			medianTurnaround = medianTurnaround(recent, now - 30.days),
			daily = daily(recent, firstDay, now.truncateToUtcDay()),
		)
	}

	private fun windowCounts(
		now: Instant,
		rows: List<ReviewLifecycle>,
		at: (ReviewLifecycle) -> Instant?,
	): WindowCounts {
		fun countSince(since: Instant) = rows.count { row -> at(row)?.let { it >= since } == true }.toLong()
		return WindowCounts(
			h24 = countSince(now - 24.hours),
			d7 = countSince(now - 7.days),
			d30 = countSince(now - 30.days),
		)
	}

	/** Median time from request to the editor returning it, over reviews returned since [since]. */
	private fun medianTurnaround(rows: List<ReviewLifecycle>, since: Instant): Duration? {
		val turnarounds = rows.mapNotNull { row ->
			row.submittedAt?.takeIf { it >= since }?.let { it - row.created }
		}.sorted()
		if (turnarounds.isEmpty()) return null
		val mid = turnarounds.size / 2
		return if (turnarounds.size % 2 == 1) {
			turnarounds[mid]
		} else {
			(turnarounds[mid - 1] + turnarounds[mid]) / 2
		}
	}

	/** Gapless per-UTC-day milestone counts, oldest first, for the trend graph. */
	private fun daily(rows: List<ReviewLifecycle>, firstDay: Instant, lastDay: Instant): List<ReviewDay> {
		fun byDay(at: (ReviewLifecycle) -> Instant?) =
			rows.mapNotNull(at).groupingBy { it.truncateToUtcDay() }.eachCount()

		val requested = byDay { it.created }
		val returned = byDay { it.submittedAt }
		val resolved = byDay { it.resolvedAt }

		val days = ArrayList<ReviewDay>()
		var day = firstDay
		while (day <= lastDay) {
			days += ReviewDay(
				day = day,
				requested = (requested[day] ?: 0).toLong(),
				returned = (returned[day] ?: 0).toLong(),
				resolved = (resolved[day] ?: 0).toLong(),
			)
			day += 1.days
		}
		return days
	}
}

/**
 * [requested], [returned] and [resolved] count each milestone by when it happened, so a
 * request sent last month and returned today shows up in today's returned count.
 */
data class ReviewStats(
	val requested: WindowCounts,
	val returned: WindowCounts,
	val resolved: WindowCounts,
	val status: ReviewStatusCounts,
	val medianTurnaround: Duration?,
	val daily: List<ReviewDay>,
)

data class ReviewDay(
	val day: Instant,
	val requested: Long,
	val returned: Long,
	val resolved: Long,
)
