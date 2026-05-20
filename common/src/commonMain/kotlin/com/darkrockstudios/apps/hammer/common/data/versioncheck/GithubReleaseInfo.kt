package com.darkrockstudios.apps.hammer.common.data.versioncheck

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
)
