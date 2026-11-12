package com.darkrockstudios.apps.hammer.services

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PerUserRateLimiterTest {

	private class FakeClock(var now: Instant = Instant.parse("2026-11-11T12:00:00Z")) : Clock {
		override fun now() = now
	}

	@Test
	fun `The window refills after a minute`() {
		val clock = FakeClock()
		val limiter = PerUserRateLimiter(clock)

		val first = (1..3).map { limiter.acquire(7, limit = 2) }
		clock.now += 59.seconds
		val beforeRefill = limiter.acquire(7, limit = 2)
		clock.now += 1.seconds
		val afterRefill = limiter.acquire(7, limit = 2)

		assertEquals(listOf(null, null, 60.seconds), first)
		assertEquals(1.seconds, beforeRefill)
		assertEquals(null, afterRefill)
	}

	@Test
	fun `A raised limit applies to the open window`() {
		val limiter = PerUserRateLimiter(FakeClock())

		limiter.acquire(7, limit = 1)
		val atOld = limiter.acquire(7, limit = 1)
		val atNew = limiter.acquire(7, limit = 2)

		assertEquals(60.seconds, atOld)
		assertEquals(null, atNew)
	}
}
