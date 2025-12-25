package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatSyncDateTest {

	@Test
	fun `formats valid SQLite datetime string`() {
		val sqliteDateTime = "2024-03-15 14:30:00"

		val result = formatSyncDate(sqliteDateTime)

		// Verify the result matches the expected format pattern
		// The exact time depends on system timezone, but format should be "MMM dd, yyyy 'at' HH:mm"
		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
	}

	@Test
	fun `formats datetime and converts from UTC to local timezone`() {
		// Use a known UTC time and verify it converts correctly
		val sqliteDateTime = "2024-06-15 12:00:00" // noon UTC

		val result = formatSyncDate(sqliteDateTime)

		// Parse the UTC time and convert to system timezone to get expected result
		val utcDateTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneId.of("UTC"))
		val localDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault())
		val expectedFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
		val expected = expectedFormatter.format(localDateTime)

		assertEquals(expected, result)
	}

	@Test
	fun `formats January date correctly`() {
		val sqliteDateTime = "2024-01-05 08:30:00"

		val result = formatSyncDate(sqliteDateTime)

		assertTrue(result.contains("Jan"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `formats December date correctly`() {
		val sqliteDateTime = "2024-12-25 18:45:00"

		val result = formatSyncDate(sqliteDateTime)

		assertTrue(result.contains("Dec"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `returns original string for invalid datetime format`() {
		val invalidDateTime = "not-a-date"

		val result = formatSyncDate(invalidDateTime)

		assertEquals("not-a-date", result)
	}

	@Test
	fun `returns original string for empty string`() {
		val emptyDateTime = ""

		val result = formatSyncDate(emptyDateTime)

		assertEquals("", result)
	}

	@Test
	fun `returns original string for partial datetime`() {
		val partialDateTime = "2024-03-15"

		val result = formatSyncDate(partialDateTime)

		assertEquals("2024-03-15", result)
	}

	@Test
	fun `returns original string for datetime with wrong separator`() {
		val wrongFormat = "2024/03/15 14:30:00"

		val result = formatSyncDate(wrongFormat)

		assertEquals("2024/03/15 14:30:00", result)
	}

	@Test
	fun `handles midnight correctly`() {
		val midnight = "2024-07-20 00:00:00"

		val result = formatSyncDate(midnight)

		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `handles end of day correctly`() {
		val endOfDay = "2024-07-20 23:59:59"

		val result = formatSyncDate(endOfDay)

		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
	}

	@Test
	fun `handles leap year date`() {
		val leapYearDate = "2024-02-29 12:00:00"

		val result = formatSyncDate(leapYearDate)

		assertTrue(result.contains("Feb"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `handles non-leap year February 29 by adjusting date`() {
		// Java's DateTimeFormatter with default resolver style may adjust invalid dates
		// 2023 is not a leap year, so Feb 29 gets parsed (may throw or adjust)
		val invalidLeapYear = "2023-02-29 12:00:00"

		val result = formatSyncDate(invalidLeapYear)

		// The parser may either return the original string (if exception) or parse it
		// Either behavior is acceptable - we just verify it doesn't crash
		assertTrue(result.isNotEmpty())
	}

	@Test
	fun `returns original string for ISO 8601 format`() {
		// SQLite format doesn't have T separator
		val isoFormat = "2024-03-15T14:30:00"

		val result = formatSyncDate(isoFormat)

		assertEquals("2024-03-15T14:30:00", result)
	}
}
