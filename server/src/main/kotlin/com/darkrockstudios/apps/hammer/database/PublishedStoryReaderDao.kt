package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

open class PublishedStoryReaderDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.publishedStoryReaderQueries

	open suspend fun recordReader(
		projectId: Long,
		dayBucket: Instant,
		visitorHash: String,
	): Unit = withContext(ioDispatcher) {
		queries.recordReader(projectId, dayBucket, visitorHash)
	}

	open suspend fun countReadersForProjectSince(projectId: Long, since: Instant): Long =
		withContext(ioDispatcher) {
			queries.countReadersForProjectSince(projectId, since).executeAsOne()
		}

	open suspend fun countReadersSince(since: Instant): Long =
		withContext(ioDispatcher) {
			queries.countReadersSince(since).executeAsOne()
		}

	open suspend fun totalReadersForProject(projectId: Long): Long =
		withContext(ioDispatcher) {
			queries.totalReadersForProject(projectId).executeAsOne() ?: 0L
		}

	open suspend fun dailyReadersForProject(projectId: Long, since: Instant): List<ReaderDay> =
		withContext(ioDispatcher) {
			queries.dailyReadersForProject(projectId, since)
				.executeAsList()
				.map { ReaderDay(it.day_bucket, it.readers) }
		}

	open suspend fun rollupReadersBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.rollupReadersBefore(cutoff)
	}

	open suspend fun deleteReadersBefore(cutoff: Instant): Unit = withContext(ioDispatcher) {
		queries.deleteReadersBefore(cutoff)
	}
}

/** Unique readers on a single UTC day, for the trend graph. */
data class ReaderDay(
	val day: Instant,
	val count: Long,
)
