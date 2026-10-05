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
		/**
		 * Substitutes a local server for the GitHub release, for end-to-end testing of a packaged
		 * build. The environment variable form exists because an installed app has no command line
		 * to put a system property on.
		 */
		const val BASE_URL_PROPERTY = "hammer.update.baseUrl"
		const val BASE_URL_ENV = "HAMMER_UPDATE_BASE_URL"

		fun defaultBaseUrl(
			property: String? = System.getProperty(BASE_URL_PROPERTY),
			environment: String? = System.getenv(BASE_URL_ENV),
		): String =
			(property ?: environment)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
				?: "${GITHUB_URL}releases/latest/download"

		fun metadataFileName(platform: Platform): String = when (platform) {
			Platform.MacOS -> "latest-mac.yml"
			Platform.Linux -> "latest-linux.yml"
			Platform.Windows, Platform.Unknown -> "latest.yml"
		}
	}
}
