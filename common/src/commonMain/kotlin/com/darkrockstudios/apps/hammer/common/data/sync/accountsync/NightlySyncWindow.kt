package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/** The local hours a nightly background sync may run in. */
object NightlySyncWindow {
	val start = LocalTime(1, 0)
	val end = LocalTime(6, 0)

	fun contains(now: Instant, timeZone: TimeZone): Boolean {
		val time = now.toLocalDateTime(timeZone).time
		return time >= start && time < end
	}

	/** Time from [now] until the window next opens, always after [now]. */
	fun untilNextStart(now: Instant, timeZone: TimeZone): Duration {
		val today = now.toLocalDateTime(timeZone).date
		val todayStart = today.atTime(start).toInstant(timeZone)
		val next = if (todayStart > now) {
			todayStart
		} else {
			today.plus(DatePeriod(days = 1)).atTime(start).toInstant(timeZone)
		}
		return next - now
	}
}
