package com.darkrockstudios.apps.hammer.common.data.versioncheck

import com.darkrockstudios.apps.hammer.common.util.stripReleaseSuffix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseInfo(
	@SerialName("tag_name")
	val tagName: String,
	val name: String? = null,
	val body: String? = null,
	@SerialName("html_url")
	val htmlUrl: String,
) {
	/**
	 * The displayable / dismissable form of the tag — bare semver with any
	 * partial-release `+platform+platform` suffix removed.
	 */
	val bareVersion: String get() = stripReleaseSuffix(tagName)
}
