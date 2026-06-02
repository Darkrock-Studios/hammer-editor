package com.darkrockstudios.apps.hammer.common.data.versioncheck

interface VersionCheckDataSource {
	suspend fun fetchLatestRelease(): GithubReleaseInfo?
}
