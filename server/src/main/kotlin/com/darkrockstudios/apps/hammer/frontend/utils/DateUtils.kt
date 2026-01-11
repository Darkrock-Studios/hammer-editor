package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatSyncDate(dateTimeStr: String): String {
	return formatSqliteDateTime(dateTimeStr, "MMM dd, yyyy 'at' HH:mm")
}

fun formatPatreonDate(dateTimeStr: String): String {
	return formatIsoDateTime(dateTimeStr, "MMM dd, yyyy 'at' HH:mm")
}

fun formatSqliteDateTime(dateTimeStr: String, pattern: String): String {
	if (dateTimeStr.isEmpty()) return ""

	return try {
		val kotlinxInstant = sqliteDateTimeStringToInstant(dateTimeStr)
		val instant = Instant.ofEpochSecond(kotlinxInstant.epochSeconds)
		formatInstant(instant, pattern)
	} catch (e: Exception) {
		dateTimeStr
	}
}

fun formatIsoDateTime(dateTimeStr: String, pattern: String): String {
	if (dateTimeStr.isEmpty()) return ""

	return try {
		val instant = Instant.parse(dateTimeStr)
		formatInstant(instant, pattern)
	} catch (e: Exception) {
		dateTimeStr
	}
}

private fun formatInstant(instant: Instant, pattern: String): String {
	val formatter = DateTimeFormatter.ofPattern(pattern)
	val zoned = instant.atZone(ZoneId.systemDefault())
	return formatter.format(zoned)
}
