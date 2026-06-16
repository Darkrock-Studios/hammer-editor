package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.database.ReaderDay
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Read/write access to the deduplicated reader rows backing the best-effort
 * "unique readers" counter. The in-memory [StoryReaderCollector] flushes keys
 * through here; the maintenance job purges past the retention window.
 */
class StoryReaderRepository(
	private val publishedStoryReaderDao: PublishedStoryReaderDao,
) : KoinComponent {

	suspend fun recordKeys(keys: List<ReaderKey>) {
		for (key in keys) {
			publishedStoryReaderDao.recordReader(key.projectId, key.dayBucket, key.visitorHash)
		}
	}

	/** All-time readers of one story (every day's unique count, summed). */
	suspend fun totalReadersForProject(projectId: Long): Long =
		publishedStoryReaderDao.totalReadersForProject(projectId)

	/** Per-day unique reader counts for one story since [since], oldest first. */
	suspend fun dailyReadersForProject(projectId: Long, since: Instant): List<ReaderDay> =
		publishedStoryReaderDao.dailyReadersForProject(projectId, since)

	/** Unique reader-sessions across all stories over the 24h / 7d / 30d windows. */
	suspend fun readerCounts(now: Instant): WindowCounts = WindowCounts(
		h24 = publishedStoryReaderDao.countReadersSince(now - 24.hours),
		d7 = publishedStoryReaderDao.countReadersSince(now - 7.days),
		d30 = publishedStoryReaderDao.countReadersSince(now - 30.days),
	)

	suspend fun purgeBefore(cutoff: Instant) {
		// Preserve the all-time total: fold the soon-to-be-purged days in first.
		publishedStoryReaderDao.rollupReadersBefore(cutoff)
		publishedStoryReaderDao.deleteReadersBefore(cutoff)
	}
}
