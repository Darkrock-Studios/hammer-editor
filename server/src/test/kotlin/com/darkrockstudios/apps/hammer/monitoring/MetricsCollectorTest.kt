package com.darkrockstudios.apps.hammer.monitoring

import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsCollectorTest {

	private val fixedNow = Instant.parse("2026-01-15T12:34:56Z")
	private val clock = object : Clock {
		override fun now(): Instant = fixedNow
	}

	private fun collector() = MetricsCollector(clock)

	@Test
	fun `records accumulate into one delta per route-method-hour with histogram bins`() {
		val c = collector()
		c.record("/api/r", "GET", 200, 30)    // le50
		c.record("/api/r", "GET", 200, 80)    // le100
		c.record("/api/r", "GET", 500, 2000)  // le2500 + error

		val deltas = c.drainToDeltas()
		assertEquals(1, deltas.size)
		val d = deltas.first()
		assertEquals("/api/r", d.route)
		assertEquals("GET", d.method)
		assertEquals(Instant.parse("2026-01-15T12:00:00Z"), d.bucketStart)
		assertEquals(3L, d.requestCount)
		assertEquals(1L, d.errorCount)
		assertEquals(2110L, d.totalDurationMs)
		assertEquals(1L, d.histogram.le50)
		assertEquals(1L, d.histogram.le100)
		assertEquals(1L, d.histogram.le2500)
		assertEquals(0L, d.histogram.leInf)
	}

	@Test
	fun `draining clears the accumulator`() {
		val c = collector()
		c.record("/api/r", "GET", 200, 10)
		assertEquals(1, c.drainToDeltas().size)
		assertTrue(c.drainToDeltas().isEmpty(), "second drain should be empty")
	}

	@Test
	fun `does nothing when collecting is disabled`() {
		val c = collector()
		c.setCollecting(false)
		c.record("/api/r", "GET", 200, 10)
		assertTrue(c.drainToDeltas().isEmpty())
		assertTrue(c.recentRequests().isEmpty())
	}

	@Test
	fun `recent requests are bounded`() {
		val c = collector()
		repeat(MetricsCollector.MAX_RECENT + 50) { c.record("/api/r", "GET", 200, 1) }
		assertEquals(MetricsCollector.MAX_RECENT, c.recentRequests().size)
	}

	@Test
	fun `bin index maps durations to the right bucket`() {
		assertEquals(0, MetricsCollector.binIndex(50))
		assertEquals(1, MetricsCollector.binIndex(51))
		assertEquals(1, MetricsCollector.binIndex(100))
		assertEquals(5, MetricsCollector.binIndex(2500))
		assertEquals(6, MetricsCollector.binIndex(2501))
	}

	@Test
	fun `percentile reads from the additive histogram`() {
		val hist = LatencyHistogram(le50 = 95, leInf = 5) // total 100
		assertEquals(50L, percentile(hist, 95.0))
		assertEquals(LATENCY_OVERFLOW_MS, percentile(hist, 99.0))
		assertEquals(0L, percentile(LatencyHistogram(), 95.0))
	}
}
