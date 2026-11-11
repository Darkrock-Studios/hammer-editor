package com.darkrockstudios.apps.hammer.common.data.changelog

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads the release notes baked in by `prepareForRelease`. No network, ever. */
class ResourceChangelogDatasource : ChangelogDatasource {

	@OptIn(ExperimentalResourceApi::class)
	@Suppress("TooGenericExceptionCaught") // A missing resource must not block app start
	override suspend fun loadChangelog(): Changelog? {
		return try {
			parseChangelog(Res.readBytes(RESOURCE_PATH).decodeToString())
		} catch (e: Exception) {
			Logger.w("Failed to read baked changelog", e)
			null
		}
	}

	companion object {
		private const val RESOURCE_PATH = "files/changelog.md"
	}
}
