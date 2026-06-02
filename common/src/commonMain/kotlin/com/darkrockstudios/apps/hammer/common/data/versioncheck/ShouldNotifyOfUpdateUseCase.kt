package com.darkrockstudios.apps.hammer.common.data.versioncheck

import com.darkrockstudios.apps.hammer.common.util.stripReleaseSuffix

/**
 * True when there's a new version the user hasn't already dismissed.
 * Comparison is on bare semvers, so partial-release suffixes (`+google-play`
 * etc.) don't fragment dismissals across stores.
 */
class ShouldNotifyOfUpdateUseCase {
	operator fun invoke(
		result: VersionCheckRepository.VersionCheckResult,
		dismissed: String?,
	): Boolean {
		if (!result.isNewVersionAvailable) return false
		val release = result.latestRelease ?: return false
		return stripReleaseSuffix(release.tagName) != dismissed?.let(::stripReleaseSuffix)
	}
}
