package com.darkrockstudios.apps.hammer.utilities

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter

fun Instant.formatLocal(format: String): String =
	toLocalDateTime(TimeZone.currentSystemDefault()).format(format)

fun LocalDateTime.format(format: String): String =
	DateTimeFormatter.ofPattern(format).format(this.toJavaLocalDateTime())

//ISO 8601
fun Instant.toISO8601(): String = toString()