package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant

fun formatSyncDate(sqliteDateTime: String): String {
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
		val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(java.time.ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		sqliteDateTime
	}
}
