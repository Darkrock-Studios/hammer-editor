package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.formatPatreonDate
import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class FormatSyncDateTest {

	@Test
	fun `formats valid datetime instant`() {
		val instant = Instant.parse("2024-03-15T14:30:00Z")

		val result = formatSyncDate(instant)

		// Verify the result matches the expected format pattern
		// The exact time depends on system timezone, but format should be "MMM dd, yyyy 'at' HH:mm"
		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
	}

	@Test
	fun `formats datetime and converts from UTC to local timezone`() {
		// Use a known UTC time and verify it converts correctly
		val instant = Instant.parse("2024-06-15T12:00:00Z") // noon UTC

		val result = formatSyncDate(instant)

		// Parse the UTC time and convert to system timezone to get expected result
		val utcDateTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneId.of("UTC"))
		val localDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault())
		val expectedFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
		val expected = expectedFormatter.format(localDateTime)

		assertEquals(expected, result)
	}

	@Test
	fun `formats January date correctly`() {
		val instant = Instant.parse("2024-01-05T08:30:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains("Jan"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `formats December date correctly`() {
		val instant = Instant.parse("2024-12-25T18:45:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains("Dec"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `handles midnight correctly`() {
		val instant = Instant.parse("2024-07-20T00:00:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `handles end of day correctly`() {
		val instant = Instant.parse("2024-07-20T23:59:59Z")

		val result = formatSyncDate(instant)

		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
	}

	@Test
	fun `handles leap year date`() {
		val instant = Instant.parse("2024-02-29T12:00:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.contains("Feb"))
		assertTrue(result.contains("2024"))
	}

	@Test
	fun `formats ISO 8601 instant`() {
		val instant = Instant.parse("2024-03-15T14:30:00Z")

		val result = formatSyncDate(instant)

		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")))
	}

	@Test
	fun `returns empty string for the pgjdbc infinity sentinel`() {
		// Postgres surfaces a `-infinity` TIMESTAMPTZ as OffsetDateTime.MIN, which is
		// outside the range java.time can render into a zoned date.
		val infinitySentinel = OffsetDateTime.MIN.toInstant().toKotlinInstant()

		val result = formatSyncDate(infinitySentinel)

		assertEquals("", result)
	}

	@Test
	fun `formatPatreonDate handles ISO 8601 format`() {
		val isoFormat = "2024-03-15T14:30:00Z"

		val result = formatPatreonDate(isoFormat)

		// Verify it matches the expected pattern (Jan 01, 2024 at 12:00)
		assertTrue(result.matches(Regex("""\w{3} \d{2}, \d{4} at \d{2}:\d{2}""")), "Result was: $result")
	}
}
