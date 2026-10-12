package com.darkrockstudios.apps.hammer.common.data.versioncheck

interface VersionCheckDatasource {
	suspend fun fetchLatestRelease(): GithubReleaseInfo?
}
