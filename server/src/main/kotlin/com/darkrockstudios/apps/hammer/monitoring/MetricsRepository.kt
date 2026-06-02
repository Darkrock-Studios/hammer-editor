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

	companion object {
		const val BUCKET_HOUR = "HOUR"
		const val BUCKET_DAY = "DAY"
	}
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
