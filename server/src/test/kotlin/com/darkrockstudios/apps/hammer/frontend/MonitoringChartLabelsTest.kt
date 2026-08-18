package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.database.ReaderDay
import com.darkrockstudios.apps.hammer.monitoring.DailyActiveUsers
import com.darkrockstudios.apps.hammer.monitoring.TimeSeriesPoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Daily chart buckets are floored to the UTC day upstream, so their labels have to be rendered in
 * UTC no matter what zone the server is configured for. Every case here uses a server zone behind
 * UTC, where a naive local-zone label would land on the previous date.
 */
class MonitoringChartLabelsTest {

	private lateinit var originalZone: TimeZone

	// UTC midnight, which is Aug 16 in New York.
	private val utcDay = Instant.parse("2026-08-17T00:00:00Z")

	@BeforeEach
	fun setServerZoneBehindUtc() {
		originalZone = TimeZone.getDefault()
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
	}

	@AfterEach
	fun restoreZone() {
		TimeZone.setDefault(originalZone)
	}

	@Test
	fun `active users chart labels the UTC day`() {
		val json = buildActiveUsersChart(listOf(DailyActiveUsers(day = utcDay, sync = 3L, web = 5L)))

		assertTrue(json.contains("Aug 17"), json)
		assertFalse(json.contains("Aug 16"), json)
	}

	@Test
	fun `readers chart labels the UTC day`() {
		val json = buildReadersChart(listOf(ReaderDay(day = utcDay, count = 7L)))

		assertTrue(json.contains("Aug 17"), json)
		assertFalse(json.contains("Aug 16"), json)
	}

	@Test
	fun `traffic chart labels the UTC day`() {
		val point = TimeSeriesPoint(bucketStart = utcDay, requests = 10L, errors = 1L, p95Ms = 200L)

		val json = buildTrafficChart(listOf(point))

		assertTrue(json.contains("Aug 17"), json)
		assertFalse(json.contains("Aug 16"), json)
	}

	@Test
	fun `daily buckets are labeled in UTC and hourly buckets in the server zone`() {
		assertEquals(ZoneOffset.UTC, chartLabelZone(hourly = false))
		assertEquals(ZoneId.of("America/New_York"), chartLabelZone(hourly = true))
	}
}
