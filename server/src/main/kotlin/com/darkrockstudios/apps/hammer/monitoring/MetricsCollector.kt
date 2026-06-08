package com.darkrockstudios.apps.hammer.monitoring

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory accumulator for API request metrics. Requests are folded into
 * per-(hour, route, method) cells with an additive latency histogram; the
 * maintenance job periodically [drainToDeltas] and persists them as HOUR
 * buckets. A bounded ring buffer of recent requests backs the live view.
 *
 * Everything here is a cheap no-op when [setCollecting] has been told the
 * feature is off, so the request path pays almost nothing when monitoring is
 * disabled. Recording is best-effort and lock-light: a handful of atomic
 * increments per request.
 */
class MetricsCollector(
	private val clock: Clock,
) {
	@Volatile
	private var collecting: Boolean = true

	private val accumulator = ConcurrentHashMap<BucketKey, Accumulator>()
	private val recentLock = Any()
	private val recent = ArrayDeque<RecentRequest>()

	/** Called by the maintenance job each tick to reflect the live config. */
	fun setCollecting(enabled: Boolean) {
		collecting = enabled
	}

	fun isCollecting(): Boolean = collecting

	fun record(route: String, method: String, status: Int, durationMs: Long) {
		if (!collecting) return

		val key = BucketKey(truncateToHour(clock.now()), route, method)
		accumulator.computeIfAbsent(key) { Accumulator() }.add(status, durationMs)

		synchronized(recentLock) {
			recent.addLast(RecentRequest(clock.now(), route, method, status, durationMs))
			while (recent.size > MAX_RECENT) recent.removeFirst()
		}
	}

	/** Atomically removes and returns the accumulated cells as persistable deltas. */
	fun drainToDeltas(): List<HourBucketDelta> {
		val out = ArrayList<HourBucketDelta>(accumulator.size)
		for (key in accumulator.keys.toList()) {
			val acc = accumulator.remove(key) ?: continue
			out += acc.toDelta(key)
		}
		return out
	}

	/** Most-recent requests, newest last. Backs the live Overview view. */
	fun recentRequests(): List<RecentRequest> = synchronized(recentLock) { recent.toList() }

	private fun truncateToHour(instant: Instant): Instant {
		val secondsPerHour = 3600L
		val epoch = instant.epochSeconds
		return Instant.fromEpochSeconds(epoch - (epoch % secondsPerHour))
	}

	private class Accumulator {
		val requestCount = AtomicLong()
		val errorCount = AtomicLong()
		val totalDurationMs = AtomicLong()
		val bins = Array(BIN_BOUNDS_MS.size + 1) { AtomicLong() }

		fun add(status: Int, durationMs: Long) {
			requestCount.incrementAndGet()
			if (status >= 500) errorCount.incrementAndGet()
			totalDurationMs.addAndGet(durationMs.coerceAtLeast(0))
			bins[binIndex(durationMs)].incrementAndGet()
		}

		fun toDelta(key: BucketKey) = HourBucketDelta(
			bucketStart = key.hour,
			route = key.route,
			method = key.method,
			requestCount = requestCount.get(),
			errorCount = errorCount.get(),
			totalDurationMs = totalDurationMs.get(),
			histogram = LatencyHistogram(
				le50 = bins[0].get(),
				le100 = bins[1].get(),
				le250 = bins[2].get(),
				le500 = bins[3].get(),
				le1000 = bins[4].get(),
				le2500 = bins[5].get(),
				leInf = bins[6].get(),
			),
		)
	}

	private data class BucketKey(val hour: Instant, val route: String, val method: String)

	companion object {
		const val MAX_RECENT = 250

		/** Upper bounds (ms) of each non-cumulative latency bin; the final bin is everything above the last bound. */
		val BIN_BOUNDS_MS = longArrayOf(50, 100, 250, 500, 1000, 2500)

		/** Index of the bin a duration falls into (exactly one bin per request). */
		fun binIndex(durationMs: Long): Int {
			for (i in BIN_BOUNDS_MS.indices) {
				if (durationMs <= BIN_BOUNDS_MS[i]) return i
			}
			return BIN_BOUNDS_MS.size
		}
	}
}

/** A single recorded request, for the live view. */
data class RecentRequest(
	val timestamp: Instant,
	val route: String,
	val method: String,
	val status: Int,
	val durationMs: Long,
)
