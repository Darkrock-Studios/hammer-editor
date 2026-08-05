package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.Res
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads the release notes baked in by `prepareForRelease`. No network, ever. */
class ResourceChangelogDatasource : ChangelogDatasource {

	@OptIn(ExperimentalResourceApi::class)
	@Suppress("TooGenericExceptionCaught") // A missing resource must not block app start
	override suspend fun loadChangelog(): Changelog? {
		return try {
			parseChangelog(Res.readBytes(RESOURCE_PATH).decodeToString())
		} catch (e: Exception) {
			Napier.w("Failed to read baked changelog", e)
			null
		}
	}

	companion object {
		private const val RESOURCE_PATH = "files/changelog.md"
	}
}
