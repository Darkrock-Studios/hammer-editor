package com.darkrockstudios.apps.hammer.frontend.admin

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class ParseExpiryTest {

	private val now = Instant.parse("2026-01-15T12:00:00Z")

	private fun parsed(preset: String?, date: String? = null): ExpiryParse.Parsed {
		val result = parseExpiry(preset, date, now)
		assertIs<ExpiryParse.Parsed>(result)
		return result
	}

	@Test
	fun `never preset means no expiry`() {
		assertNull(parsed("never").expires)
	}

	@Test
	fun `absent or blank preset means no expiry`() {
		assertNull(parsed(null).expires)
		assertNull(parsed("").expires)
	}

	@Test
	fun `day presets resolve relative to now`() {
		assertEquals(now + 7.days, parsed("7").expires)
		assertEquals(now + 30.days, parsed("30").expires)
		assertEquals(now + 90.days, parsed("90").expires)
	}

	@Test
	fun `custom date expires at the end of the chosen day`() {
		val expires = parsed("custom", "2026-03-01").expires

		// The whole of March 1st in the server's zone is still whitelisted.
		val endOfDay = LocalDate.parse("2026-03-01")
			.plusDays(1)
			.atStartOfDay(ZoneId.systemDefault())
			.toInstant()
			.toKotlinInstant()
		assertEquals(endOfDay, expires)
	}

	@Test
	fun `custom with a blank date means no expiry`() {
		assertNull(parsed("custom", "").expires)
		assertNull(parsed("custom", null).expires)
		assertNull(parsed("custom", "   ").expires)
	}

	@Test
	fun `custom with an unparseable date is invalid`() {
		assertTrue(parseExpiry("custom", "not-a-date", now) is ExpiryParse.Invalid)
		assertTrue(parseExpiry("custom", "2026-13-45", now) is ExpiryParse.Invalid)
		assertTrue(parseExpiry("custom", "03/01/2026", now) is ExpiryParse.Invalid)
	}

	@Test
	fun `unknown or nonpositive presets are invalid`() {
		assertTrue(parseExpiry("bogus", null, now) is ExpiryParse.Invalid)
		assertTrue(parseExpiry("0", null, now) is ExpiryParse.Invalid)
		assertTrue(parseExpiry("-7", null, now) is ExpiryParse.Invalid)
	}

	@Test
	fun `preset matching is case and whitespace insensitive`() {
		assertNull(parsed("  NEVER  ").expires)
		assertEquals(now + 7.days, parsed("  7  ").expires)
	}
}
