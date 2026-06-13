package com.darkrockstudios.apps.hammer.common.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Instant

actual fun Instant.formatLocal(format: String): String {
	val formatter = NSDateFormatter().apply { dateFormat = format }
	return formatter.stringFromDate(toNSDate())
}

actual fun LocalDateTime.format(format: String): String {
	val instant = toInstant(TimeZone.currentSystemDefault())
	val formatter = NSDateFormatter().apply { dateFormat = format }
	return formatter.stringFromDate(instant.toNSDate())
}

private fun Instant.toNSDate(): NSDate {
	val seconds = epochSeconds.toDouble() + nanosecondsOfSecond / 1_000_000_000.0
	return NSDate.dateWithTimeIntervalSince1970(seconds)
}
