package com.darkrockstudios.apps.hammer.common.data.changelog

interface ChangelogDatasource {
	suspend fun loadChangelog(): Changelog?
}
