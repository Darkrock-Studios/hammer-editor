package com.darkrockstudios.apps.hammer.utilities

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

fun Instant.formatLocal(format: String): String =
	toLocalDateTime(TimeZone.currentSystemDefault()).format(format)

fun LocalDateTime.format(format: String): String =
	DateTimeFormatter.ofPattern(format).format(this.toJavaLocalDateTime())

//ISO 8601
fun Instant.toISO8601(): String = toString()

// SQLite Date/Time formatting
private val UTC = ZoneId.of("UTC")
private val sqliteDatetimeFormatter =
	DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC)

fun sqliteDateTimeStringToInstant(dateTimeString: String): Instant {
	val localDateTime = java.time.LocalDateTime.parse(dateTimeString, sqliteDatetimeFormatter)
	return localDateTime.atZone(UTC).toInstant().toKotlinInstant()
}

fun Instant.toSqliteDateTimeString(): String {
	return sqliteDatetimeFormatter.format(toJavaInstant())
}