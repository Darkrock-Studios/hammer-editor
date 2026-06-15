package com.darkrockstudios.apps.hammer.monitoring

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory accumulator of distinct (user, activity type, hour) keys for the
 * "Active Users" dashboard metric. The maintenance job periodically
 * [drainToKeys] and persists them. Recording is a lock-light concurrent-set add
 * and a cheap no-op when [setCollecting] has been told the feature is off.
 */
class UserActivityCollector(
	private val clock: Clock,
) {
	@Volatile
	private var collecting: Boolean = true

	private val keys = ConcurrentHashMap.newKeySet<ActivityKey>()

	/** Called by the maintenance job each tick to reflect the live config. */
	fun setCollecting(enabled: Boolean) {
		collecting = enabled
	}

	fun isCollecting(): Boolean = collecting

	fun record(userId: Long, type: ActivityType) {
		if (!collecting) return
		keys.add(ActivityKey(userId, type, truncateToHour(clock.now())))
	}

	/** Atomically removes and returns the accumulated keys. */
	fun drainToKeys(): List<ActivityKey> {
		val out = ArrayList<ActivityKey>(keys.size)
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			out += iterator.next()
			iterator.remove()
		}
		return out
	}

	private fun truncateToHour(instant: Instant): Instant {
		val secondsPerHour = 3600L
		val epoch = instant.epochSeconds
		return Instant.fromEpochSeconds(epoch - (epoch % secondsPerHour))
	}
}

/** A distinct unit of user activity: one user, one activity type, within one hour. */
data class ActivityKey(
	val userId: Long,
	val type: ActivityType,
	val hourBucket: Instant,
)

enum class ActivityType(val dbValue: String) {
	SYNC("SYNC"),
	WEB("WEB"),
}
