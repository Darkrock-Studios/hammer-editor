package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.Api_metric_bucket
import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

/**
 * Read/write access to rolled-up API metrics. The in-memory collector (Phase 2)
 * writes HOUR buckets through here; the maintenance job rolls up and trims.
 */
class MetricsRepository(
	private val apiMetricDao: ApiMetricDao,
) : KoinComponent {

	suspend fun recordHourBucket(bucket: HourBucketDelta) {
		apiMetricDao.upsertBucket(
			bucketStart = bucket.bucketStart,
			bucketSize = BUCKET_HOUR,
			route = bucket.route,
			method = bucket.method,
			requestCount = bucket.requestCount,
			errorCount = bucket.errorCount,
			totalDurationMs = bucket.totalDurationMs,
			le50 = bucket.histogram.le50,
			le100 = bucket.histogram.le100,
			le250 = bucket.histogram.le250,
			le500 = bucket.histogram.le500,
			le1000 = bucket.histogram.le1000,
			le2500 = bucket.histogram.le2500,
			leInf = bucket.histogram.leInf,
		)
	}

	/** Roll HOUR buckets older than [hourCutoff] into DAY buckets and purge DAY buckets older than [dayCutoff]. */
	suspend fun rollupAndTrim(hourCutoff: Instant, dayCutoff: Instant) {
		apiMetricDao.rollupHourToDay(hourCutoff)
		apiMetricDao.deleteDayBucketsBefore(dayCutoff)
	}

	suspend fun getHourBucketsSince(since: Instant): List<Api_metric_bucket> =
		apiMetricDao.getBucketsSince(BUCKET_HOUR, since)

	suspend fun getDayBucketsSince(since: Instant): List<Api_metric_bucket> =
		apiMetricDao.getBucketsSince(BUCKET_DAY, since)

	/**
	 * Per-endpoint stats since [since], merging HOUR and DAY buckets (a given
	 * instant lives in exactly one resolution, so summing both is safe). Sorted
	 * by request volume, busiest first.
	 */
	suspend fun getEndpointStats(since: Instant): List<EndpointStat> {
		val all = getHourBucketsSince(since) + getDayBucketsSince(since)
		return all.groupBy { it.route to it.method }
			.map { (key, rows) ->
				val requests = rows.sumOf { it.request_count }
				val errors = rows.sumOf { it.error_count }
				val totalMs = rows.sumOf { it.total_duration_ms }
				val histogram = rows.fold(LatencyHistogram()) { acc, r -> acc.plus(r) }
				EndpointStat(
					route = key.first,
					method = key.second,
					requestCount = requests,
					errorCount = errors,
					avgMs = if (requests > 0) totalMs / requests else 0,
					p50 = percentile(histogram, 50.0),
					p95 = percentile(histogram, 95.0),
					p99 = percentile(histogram, 99.0),
				)
			}
			.sortedByDescending { it.requestCount }
	}

	/** Server-wide totals across all endpoints since [since]. */
	suspend fun getTotals(since: Instant): MetricTotals {
		val stats = getEndpointStats(since)
		val requests = stats.sumOf { it.requestCount }
		val errors = stats.sumOf { it.errorCount }
		val histogram = (getHourBucketsSince(since) + getDayBucketsSince(since))
			.fold(LatencyHistogram()) { acc, r -> acc.plus(r) }
		return MetricTotals(
			requestCount = requests,
			errorCount = errors,
			errorRate = if (requests > 0) errors.toDouble() / requests else 0.0,
			p95Ms = percentile(histogram, 95.0),
		)
	}

	suspend fun getTimeSeries(since: Instant, hourly: Boolean): List<TimeSeriesPoint> {
		val raw = if (hourly) getHourBucketsSince(since) else getDayBucketsSince(since)
		return raw.groupBy { it.bucket_start }.entries
			.sortedBy { it.key }
			.map { (ts, rows) ->
				val requests = rows.sumOf { it.request_count }
				val errors = rows.sumOf { it.error_count }
				val histogram = rows.fold(LatencyHistogram()) { acc, r -> acc.plus(r) }
				val p95 = percentile(histogram, 95.0).let { if (it == LATENCY_OVERFLOW_MS) 2500L else it }
				TimeSeriesPoint(ts, requests, errors, p95)
			}
	}

	companion object {
		const val BUCKET_HOUR = "HOUR"
		const val BUCKET_DAY = "DAY"
	}
}

data class TimeSeriesPoint(
	val bucketStart: Instant,
	val requests: Long,
	val errors: Long,
	val p95Ms: Long,
)

data class EndpointStat(
	val route: String,
	val method: String,
	val requestCount: Long,
	val errorCount: Long,
	val avgMs: Long,
	val p50: Long,
	val p95: Long,
	val p99: Long,
)

data class MetricTotals(
	val requestCount: Long,
	val errorCount: Long,
	val errorRate: Double,
	val p95Ms: Long,
)

/** Upper bounds (ms) for each latency bin; the final bin (leInf) is everything above the last bound. */
private val LATENCY_BIN_BOUNDS = longArrayOf(50, 100, 250, 500, 1000, 2500, Long.MAX_VALUE)

/** Sentinel returned for a percentile that lands in the overflow (>2500ms) bin. */
const val LATENCY_OVERFLOW_MS = Long.MAX_VALUE

private fun LatencyHistogram.counts(): LongArray =
	longArrayOf(le50, le100, le250, le500, le1000, le2500, leInf)

/** Add the histogram bins of a persisted bucket row onto this histogram. */
private fun LatencyHistogram.plus(row: Api_metric_bucket) = LatencyHistogram(
	le50 = le50 + row.le_50,
	le100 = le100 + row.le_100,
	le250 = le250 + row.le_250,
	le500 = le500 + row.le_500,
	le1000 = le1000 + row.le_1000,
	le2500 = le2500 + row.le_2500,
	leInf = leInf + row.le_inf,
)

/**
 * Approximate percentile (ms) from the additive histogram: returns the upper
 * bound of the bin where the cumulative count crosses [p]%. Overflow bin yields
 * [LATENCY_OVERFLOW_MS]. Returns 0 when there's no data.
 */
fun percentile(histogram: LatencyHistogram, p: Double): Long {
	val counts = histogram.counts()
	val total = counts.sum()
	if (total == 0L) return 0
	val threshold = Math.ceil(p / 100.0 * total).toLong().coerceAtLeast(1)
	var cumulative = 0L
	for (i in counts.indices) {
		cumulative += counts[i]
		if (cumulative >= threshold) return LATENCY_BIN_BOUNDS[i]
	}
	return LATENCY_BIN_BOUNDS.last()
}

/** A flush of accumulated request stats for a single (hour, route, method) cell. */
data class HourBucketDelta(
	val bucketStart: Instant,
	val route: String,
	val method: String,
	val requestCount: Long,
	val errorCount: Long,
	val totalDurationMs: Long,
	val histogram: LatencyHistogram,
)

/** Additive latency histogram: counts of requests whose duration was <= each bound (ms). */
data class LatencyHistogram(
	val le50: Long = 0,
	val le100: Long = 0,
	val le250: Long = 0,
	val le500: Long = 0,
	val le1000: Long = 0,
	val le2500: Long = 0,
	val leInf: Long = 0,
)
