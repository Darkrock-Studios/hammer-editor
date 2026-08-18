package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerTimeZoneTest {

	private fun env(vararg entries: Pair<String, String>): (String) -> String? {
		val map = entries.toMap()
		return { map[it] }
	}

	@Test
	fun `config timezone wins over both environment variables`() {
		val zone = resolveServerTimeZone(
			configured = "Europe/Paris",
			readEnv = env(TIMEZONE_ENV_VAR to "Asia/Tokyo", "TZ" to "America/New_York"),
		)

		assertEquals(ZoneId.of("Europe/Paris"), zone)
	}

	@Test
	fun `HAMMER_TIMEZONE is used when the config leaves it unset`() {
		val zone = resolveServerTimeZone(
			configured = null,
			readEnv = env(TIMEZONE_ENV_VAR to "Asia/Tokyo", "TZ" to "America/New_York"),
		)

		assertEquals(ZoneId.of("Asia/Tokyo"), zone)
	}

	@Test
	fun `TZ is used when nothing else sets a zone`() {
		val zone = resolveServerTimeZone(configured = null, readEnv = env("TZ" to "America/New_York"))

		assertEquals(ZoneId.of("America/New_York"), zone)
	}

	@Test
	fun `a TZ with the POSIX colon prefix is accepted`() {
		val zone = resolveServerTimeZone(configured = null, readEnv = env("TZ" to ":Europe/Paris"))

		assertEquals(ZoneId.of("Europe/Paris"), zone)
	}

	@Test
	fun `falls back to the host zone when nothing is set`() {
		val zone = resolveServerTimeZone(configured = null, readEnv = env())

		assertEquals(ZoneId.systemDefault(), zone)
	}

	@Test
	fun `blank settings are ignored`() {
		val zone = resolveServerTimeZone(
			configured = "  ",
			readEnv = env(TIMEZONE_ENV_VAR to "", "TZ" to "Asia/Tokyo"),
		)

		assertEquals(ZoneId.of("Asia/Tokyo"), zone)
	}

	@Test
	fun `an unknown config timezone aborts with the offending value`() {
		val error = assertFailsWith<IllegalArgumentException> {
			resolveServerTimeZone(configured = "Europe/Paree", readEnv = env())
		}

		assertTrue(error.message!!.contains("Europe/Paree"), "Message was: ${error.message}")
	}

	@Test
	fun `an unknown HAMMER_TIMEZONE aborts`() {
		val error = assertFailsWith<IllegalArgumentException> {
			resolveServerTimeZone(configured = null, readEnv = env(TIMEZONE_ENV_VAR to "Not/AZone"))
		}

		assertTrue(error.message!!.contains(TIMEZONE_ENV_VAR), "Message was: ${error.message}")
	}

	@Test
	fun `a POSIX-form TZ falls through to the host zone instead of aborting`() {
		val zone = resolveServerTimeZone(configured = null, readEnv = env("TZ" to "CET-1CEST,M3.5.0,M10.5.0/3"))

		assertEquals(ZoneId.systemDefault(), zone)
	}

	@Test
	fun `applying a zone makes it the JVM default`() {
		val original = TimeZone.getDefault()
		try {
			applyServerTimeZone(ZoneId.of("Pacific/Chatham"))

			assertEquals(ZoneId.of("Pacific/Chatham"), ZoneId.systemDefault())
		} finally {
			// Back through apply(), not setDefault(), so the logging config is restored with it.
			applyServerTimeZone(original.toZoneId())
		}
	}
}
