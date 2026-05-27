package com.darkrockstudios.apps.hammer.frontend.utils

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

/** Format a kotlin.time.Instant with the given pattern in the system zone. */
fun formatInstant(instant: kotlin.time.Instant, pattern: String): String {
	val formatter = DateTimeFormatter.ofPattern(pattern)
	val zoned = instant.toJavaInstant().atZone(ZoneId.systemDefault())
	return formatter.format(zoned)
}
