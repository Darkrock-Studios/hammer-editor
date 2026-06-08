package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Api_metric_bucket
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

open class ApiMetricDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.apiMetricQueries

	open suspend fun upsertBucket(
		bucketStart: Instant,
		bucketSize: String,
		route: String,
		method: String,
		requestCount: Long,
		errorCount: Long,
		totalDurationMs: Long,
		le50: Long,
		le100: Long,
		le250: Long,
		le500: Long,
		le1000: Long,
		le2500: Long,
		leInf: Long,
	): Unit = withContext(ioDispatcher) {
		queries.upsertBucket(
			bucketStart, bucketSize, route, method,
			requestCount, errorCount, totalDurationMs,
			le50, le100, le250, le500, le1000, le2500, leInf,
		)
	}

	/** Fold HOUR buckets older than [cutoff] into DAY buckets, then drop the HOUR rows. */
	open suspend fun rollupHourToDay(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.transaction {
			queries.rollupHourToDay(cutoff)
			queries.deleteHourBucketsBefore(cutoff)
		}
	}

	open suspend fun deleteDayBucketsBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.deleteDayBucketsBefore(cutoff)
	}

	open suspend fun getBucketsSince(bucketSize: String, since: Instant): List<Api_metric_bucket> =
		withContext(ioDispatcher) {
			queries.getBucketsSince(bucketSize, since).executeAsList()
		}
}
