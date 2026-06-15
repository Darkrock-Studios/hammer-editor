package com.darkrockstudios.apps.hammer.frontend.utils

import java.time.DateTimeException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

/** Format an [Instant] for display in the dashboard's sync log. */
fun formatSyncDate(instant: kotlin.time.Instant): String =
	formatInstant(instant, "MMM dd, yyyy 'at' HH:mm")

/** Format a Patreon ISO datetime string for display. Patreon hands us strings, not Instants. */
fun formatPatreonDate(dateTimeStr: String): String {
	if (dateTimeStr.isEmpty()) return ""
	return try {
		formatInstant(kotlin.time.Instant.parse(dateTimeStr), "MMM dd, yyyy 'at' HH:mm")
	} catch (_: Exception) {
		dateTimeStr
	}
}

/** Format a kotlin.time.Instant with the given pattern, in [zone] (system zone by default). */
fun formatInstant(
	instant: kotlin.time.Instant,
	pattern: String,
	zone: ZoneId = ZoneId.systemDefault(),
): String {
	val formatter = DateTimeFormatter.ofPattern(pattern)
	// pgjdbc surfaces a Postgres `±infinity` TIMESTAMPTZ as OffsetDateTime.MIN/MAX,
	// which falls outside the range java.time can resolve into a zoned date.
	val zoned = try {
		instant.toJavaInstant().atZone(zone)
	} catch (_: DateTimeException) {
		return ""
	}
	return formatter.format(zoned)
}
