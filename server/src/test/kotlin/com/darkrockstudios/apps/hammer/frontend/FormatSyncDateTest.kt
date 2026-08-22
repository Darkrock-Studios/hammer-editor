package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.SYNC_DATE_PATTERN
import com.darkrockstudios.apps.hammer.frontend.utils.formatInstant
import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class FormatSyncDateTest {

	// Month names are locale-dependent; derive the expected token from the same
	// FORMAT-category locale DateTimeFormatter.ofPattern uses.
	private fun monthShort(month: Month): String =
		month.getDisplayName(TextStyle.SHORT, Locale.getDefault(Locale.Category.FORMAT))

	private val syncDateFormat = Regex("""\S+ \d{2}, \d{4} at \d{2}:\d{2}""")

	@Test
	fun `formats valid datetime instant`() {
		val instant = Instant.parse("2024-03-15T14:30:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.matches(syncDateFormat), "Result was: $result")
		assertTrue(result.startsWith(monthShort(Month.MARCH)), "Result was: $result")
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `formats datetime and converts from UTC to local timezone`() {
		val instant = Instant.parse("2024-06-15T12:00:00Z") // noon UTC

		val result = formatSyncDate(instant)

		val utcDateTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneId.of("UTC"))
		val localDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault())
		val expected = DateTimeFormatter.ofPattern(SYNC_DATE_PATTERN).format(localDateTime)

		assertEquals(expected, result)
	}

	@Test
	fun `formats January date correctly`() {
		val instant = Instant.parse("2024-01-05T08:30:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains(monthShort(Month.JANUARY)), "Result was: $result")
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `formats December date correctly`() {
		val instant = Instant.parse("2024-12-25T18:45:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains(monthShort(Month.DECEMBER)), "Result was: $result")
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `handles midnight correctly`() {
		val instant = Instant.parse("2024-07-20T00:00:00Z")

		val result = formatInstant(instant, SYNC_DATE_PATTERN, ZoneId.of("UTC"))

		assertTrue(result.matches(syncDateFormat), "Result was: $result")
		assertTrue(result.endsWith("at 00:00"), "Result was: $result")
	}

	@Test
	fun `handles end of day correctly`() {
		val instant = Instant.parse("2024-07-20T23:59:59Z")

		val result = formatInstant(instant, SYNC_DATE_PATTERN, ZoneId.of("UTC"))

		assertTrue(result.matches(syncDateFormat), "Result was: $result")
		assertTrue(result.endsWith("at 23:59"), "Result was: $result")
	}

	@Test
	fun `handles leap year date`() {
		val instant = Instant.parse("2024-02-29T12:00:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains(monthShort(Month.FEBRUARY)), "Result was: $result")
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `returns empty string for the pgjdbc infinity sentinel`() {
		// Postgres surfaces a `-infinity` TIMESTAMPTZ as OffsetDateTime.MIN, which is
		// outside the range java.time can render into a zoned date.
		val infinitySentinel = OffsetDateTime.MIN.toInstant().toKotlinInstant()

		val result = formatSyncDate(infinitySentinel)

		assertEquals("", result)
	}

}
