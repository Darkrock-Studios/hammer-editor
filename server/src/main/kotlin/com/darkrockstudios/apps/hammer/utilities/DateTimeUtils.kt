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

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Floor an instant to the start (00:00) of its UTC day. */
fun Instant.truncateToUtcDay(): Instant =
	Instant.fromEpochMilliseconds(toEpochMilliseconds() / MILLIS_PER_DAY * MILLIS_PER_DAY)

fun LocalDateTime.format(format: String): String =
	DateTimeFormatter.ofPattern(format).format(this.toJavaLocalDateTime())

//ISO 8601
fun Instant.toISO8601(): String = toString()

/**
 * Coerce an instant into the range a Postgres `TIMESTAMPTZ` column can hold. Instants near
 * [Instant.DISTANT_PAST]..[Instant.DISTANT_FUTURE] round-trip; values beyond reach further than an
 * `OffsetDateTime`'s `LocalDate` can represent and would throw when encoded. Those extremes only ever
 * arise from garbage input, so clamping to the distant-past/future sentinels preserves ordering.
 */
fun Instant.coerceToStorableRange(): Instant =
	coerceIn(Instant.DISTANT_PAST, Instant.DISTANT_FUTURE)

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

/**
 * Parse a legacy SQLite-stored timestamp into a kotlinx [Instant]. Accepts
 * both the canonical `"YYYY-MM-DD HH:MM:SS"` form (the SQLite default for
 * `datetime('now')`) and ISO-8601 strings with or without a trailing `Z`.
 * Throws on unparseable input. Used by the one-shot SQLite→Postgres migrator
 * and the parity checker.
 */
fun parseLegacyTimestamp(text: String): Instant {
	val cleaned = text.trim()
	val iso = runCatching {
		Instant.parse(cleaned.replace(' ', 'T').let { if (it.endsWith("Z")) it else "${it}Z" })
	}.getOrNull()
	if (iso != null) return iso
	return sqliteDateTimeStringToInstant(cleaned)
}