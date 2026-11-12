package com.darkrockstudios.apps.hammer.services

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A fixed window per user. The limit is passed on every call rather than fixed at
 * construction, so an admin's change applies at once.
 */
class PerUserRateLimiter(
	private val clock: Clock,
	private val window: Duration = 1.minutes,
) {
	private data class Window(val start: Instant, val count: Int)

	private val windows = ConcurrentHashMap<Long, Window>()

	@Volatile
	private var lastSweep: Instant = clock.now()

	/** Null when the request is allowed; otherwise how long until [userId]'s window refills. */
	fun acquire(userId: Long, limit: Int): Duration? {
		val now = clock.now()
		sweep(now)
		var allowed = false
		val current = windows.compute(userId) { _, current ->
			val open = current?.takeIf { now - it.start < window } ?: Window(now, 0)
			if (open.count < limit) {
				allowed = true
				open.copy(count = open.count + 1)
			} else {
				open
			}
		}!!
		return if (allowed) null else window - (now - current.start)
	}

	private fun sweep(now: Instant) {
		if (now - lastSweep < window) return
		lastSweep = now
		windows.entries.removeIf { now - it.value.start >= window }
	}
}
