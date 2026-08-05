package com.darkrockstudios.apps.hammer.common.data.versioncheck

import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import com.darkrockstudios.apps.hammer.common.util.isNewVersionAvailable
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * App-scoped cache of the latest GitHub release. One HTTP request per session
 * unless [checkForUpdate] is called with `force = true`.
 *
 * The protocol mismatch dialog is the only consumer, and it only appears once the user has
 * already connected to a sync server. Nothing on the app-load path may use this — the in-app
 * changelog is baked in at release time precisely so startup never calls GitHub.
 */
class VersionCheckRepository(
	private val dataSource: VersionCheckDataSource,
) {
	private val lock = reentrantLock()
	private var cached: VersionCheckResult? = null

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
		return result
	}

	data class VersionCheckResult(
		val latestRelease: GithubReleaseInfo?,
		val isNewVersionAvailable: Boolean,
		val currentVersion: String,
	)
}
