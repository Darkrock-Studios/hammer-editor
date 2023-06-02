package com.darkrockstudios.apps.hammer.utilities

import kotlinx.datetime.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Instant.formatLocal(format: String): String =
	toLocalDateTime(TimeZone.currentSystemDefault()).format(format)

fun LocalDateTime.format(format: String): String =
	DateTimeFormatter.ofPattern(format).format(this.toJavaLocalDateTime())

//ISO 8601
fun Instant.toISO8601(): String {
	//val tz: TimeZone = TimeZone.UTC
	val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
		.withZone(ZoneId.of("UTC"))
	return formatter.format(this.toJavaInstant())
}