package com.darkrockstudios.apps.hammer.common.data.versioncheck

import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import com.darkrockstudios.apps.hammer.common.util.isNewVersionAvailable
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-scoped cache of the latest GitHub release. One HTTP request per session
 * unless [checkForUpdate] is called with `force = true`. Subscribers receive
 * the cached result immediately via the `replay = 1` flow.
 */
class VersionCheckRepository(
	private val dataSource: VersionCheckDataSource,
) {
	private val lock = reentrantLock()
	private var cached: VersionCheckResult? = null

	private val _updates = MutableSharedFlow<VersionCheckResult>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val updates: SharedFlow<VersionCheckResult> = _updates

	suspend fun checkForUpdate(force: Boolean = false): VersionCheckResult {
		val existing = lock.withLock { cached }
		if (existing != null && !force) return existing

		val release = dataSource.fetchLatestRelease()
		val result = VersionCheckResult(
			latestRelease = release,
			isNewVersionAvailable = release?.tagName?.let { isNewVersionAvailable(it) } == true,
			currentVersion = getAppVersionString(),
		)
		lock.withLock { cached = result }
		_updates.tryEmit(result)
		return result
	}

	fun currentResult(): VersionCheckResult? = lock.withLock { cached }

	data class VersionCheckResult(
		val latestRelease: GithubReleaseInfo?,
		val isNewVersionAvailable: Boolean,
		val currentVersion: String,
	)
}
