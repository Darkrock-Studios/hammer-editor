package utils

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class TestClock(baseClock: Clock) : Clock {
	private val baseTime = baseClock.now()
	private var offset: Duration = 0.milliseconds

	fun advanceTime(duration: Duration) {
		offset += duration
	}

	fun setOffset(duration: Duration) {
		offset = duration
	}

	override fun now(): Instant = baseTime + offset
}