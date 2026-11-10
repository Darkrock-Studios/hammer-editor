package com.darkrockstudios.apps.hammer.health

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LogThrottleTest {

	private val time = TestTimeSource()

	@Test
	fun `a key logs once per interval`() {
		val throttle = LogThrottle(1.minutes, time)

		val first = throttle.shouldLog("10.0.0.1")
		time += 59.seconds
		val tooSoon = throttle.shouldLog("10.0.0.1")
		time += 1.seconds
		val again = throttle.shouldLog("10.0.0.1")

		assertEquals(listOf(true, false, true), listOf(first, tooSoon, again))
	}

	@Test
	fun `keys are throttled independently`() {
		val throttle = LogThrottle(1.minutes, time)

		throttle.shouldLog("10.0.0.1")

		assertTrue(throttle.shouldLog("10.0.0.2"))
		assertFalse(throttle.shouldLog("10.0.0.1"))
	}

	@Test
	fun `the key map is bounded`() {
		val throttle = LogThrottle(1.minutes, time, maxKeys = 2)
		throttle.shouldLog("a")
		throttle.shouldLog("b")

		throttle.shouldLog("c")

		// Hitting the bound forgets earlier keys, so "a" may log again.
		assertTrue(throttle.shouldLog("a"))
	}
}
