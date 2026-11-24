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

		val first = throttle.shouldLog("503 /health database down")
		time += 59.seconds
		val tooSoon = throttle.shouldLog("503 /health database down")
		time += 1.seconds
		val again = throttle.shouldLog("503 /health database down")

		assertEquals(listOf(true, false, true), listOf(first, tooSoon, again))
	}

	@Test
	fun `keys are throttled independently`() {
		val throttle = LogThrottle(1.minutes, time)

		throttle.shouldLog("503 /health database down")

		assertTrue(throttle.shouldLog("401 /health/database no token"))
		assertFalse(throttle.shouldLog("503 /health database down"))
	}
}
