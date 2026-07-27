package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

data class CommunityStats(val authors: Long, val stories: Long)

/**
 * The header's community badge appears on every page, so these two counts are cached rather
 * than queried per render. A badge that lags a new author by a minute costs nothing; two
 * extra queries on every page view do.
 */
class CommunityStatsProvider(
	private val accountsRepository: AccountsRepository,
	private val projectAccessRepository: ProjectAccessRepository,
) {
	private val refreshLock = Mutex()

	@Volatile
	private var cached: CommunityStats? = null

	@Volatile
	private var cachedAt: TimeSource.Monotonic.ValueTimeMark? = null

	suspend fun get(): CommunityStats {
		fresh()?.let { return it }

		return refreshLock.withLock {
			// Another caller may have refreshed while this one waited for the lock.
			fresh() ?: query().also {
				cached = it
				cachedAt = TimeSource.Monotonic.markNow()
			}
		}
	}

	private fun fresh(): CommunityStats? {
		val value = cached ?: return null
		val at = cachedAt ?: return null
		return value.takeIf { at.elapsedNow() < TTL }
	}

	private suspend fun query() = CommunityStats(
		authors = accountsRepository.countCommunityAuthors(),
		stories = projectAccessRepository.countCommunityFeedStories(),
	)

	private companion object {
		val TTL = 60.seconds
	}
}
