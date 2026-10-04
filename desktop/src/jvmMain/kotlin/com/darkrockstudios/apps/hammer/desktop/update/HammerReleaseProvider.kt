package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.updater.provider.UpdateProvider

/**
 * Points the Nucleus updater at the fixed-name assets on the latest GitHub release.
 *
 * Nucleus's own GitHubProvider downloads from `releases/download/v{version}/`, which does not
 * exist for a partial-release tag such as `v1.2.4+google-play`. `releases/latest/download/` is a
 * plain redirect to whatever release is marked latest: no API call, no token, no rate limit, and
 * the same URLs the README links.
 */
class HammerReleaseProvider(
	private val baseUrl: String = defaultBaseUrl(),
) : UpdateProvider {

	override fun getUpdateMetadataUrl(channel: String, platform: Platform): String =
		"$baseUrl/${metadataFileName(platform)}"

	override fun getDownloadUrl(fileName: String, version: String): String = "$baseUrl/$fileName"

	companion object {
		/** Substitutes a local server for the GitHub release, for end-to-end testing of a packaged build. */
		const val BASE_URL_PROPERTY = "hammer.update.baseUrl"

		fun defaultBaseUrl(): String =
			System.getProperty(BASE_URL_PROPERTY)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
				?: "${GITHUB_URL}releases/latest/download"

		fun metadataFileName(platform: Platform): String = when (platform) {
			Platform.MacOS -> "latest-mac.yml"
			Platform.Linux -> "latest-linux.yml"
			Platform.Windows, Platform.Unknown -> "latest.yml"
		}
	}
}
