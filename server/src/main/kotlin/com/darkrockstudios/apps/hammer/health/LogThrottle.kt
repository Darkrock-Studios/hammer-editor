package com.darkrockstudios.apps.hammer.health

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Lets a message through at most once per [interval] per key. Health probes repeat every few
 * seconds, so an outage or a monitor with a stale token would otherwise flood the log.
 */
class LogThrottle(
	private val interval: Duration,
	private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
	private val maxKeys: Int = 1_000,
) {
	private val lastLogged = ConcurrentHashMap<String, ComparableTimeMark>()

	fun shouldLog(key: String): Boolean {
		// Keys can come from client addresses; bound the map rather than let them pile up.
		if (lastLogged.size >= maxKeys && !lastLogged.containsKey(key)) lastLogged.clear()
		var allowed = false
		lastLogged.compute(key) { _, last ->
			if (last == null || last.elapsedNow() >= interval) {
				allowed = true
				timeSource.markNow()
			} else {
				last
			}
		}
		return allowed
	}
}
