package com.darkrockstudios.apps.hammer.common.data.versioncheck

import com.darkrockstudios.apps.hammer.base.VERSION_CHECK_URL
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Falls back to the annotated-tag message when the release `body` is blank —
 * GitHub's web UI silently does the same, but the `/releases/latest` endpoint
 * doesn't, so otherwise users would see the page populated and our dialog empty.
 */
class GithubVersionCheckDataSource(
	private val http: HttpClient,
	private val json: Json,
) : VersionCheckDataSource {

	private val apiBase: String = VERSION_CHECK_URL.substringBefore("/releases/latest")

	override suspend fun fetchLatestRelease(): GithubReleaseInfo? {
		return try {
			val response = http.get(VERSION_CHECK_URL)
			if (!response.status.isSuccess()) {
				Napier.w("Version check returned ${response.status} (likely GitHub rate limit)")
				return null
			}
			val release = json.decodeFromString<GithubReleaseInfo>(response.bodyAsText())
			if (release.body.isNullOrBlank()) {
				release.copy(body = fetchAnnotatedTagMessage(release.tagName))
			} else {
				release
			}
		} catch (e: Exception) {
			Napier.w("Failed to fetch latest app version", e)
			null
		}
	}

	private suspend fun fetchAnnotatedTagMessage(tagName: String): String? {
		return try {
			val refResponse = http.get("$apiBase/git/refs/tags/$tagName")
			val ref = json.decodeFromString<GitRef>(refResponse.bodyAsText())
			if (ref.gitObject.type != "tag") return null

			val tagResponse = http.get("$apiBase/git/tags/${ref.gitObject.sha}")
			json.decodeFromString<GitTag>(tagResponse.bodyAsText()).message
		} catch (e: Exception) {
			Napier.d("No annotated tag message available for $tagName", e)
			null
		}
	}

	@Serializable
	private data class GitRef(
		@kotlinx.serialization.SerialName("object")
		val gitObject: GitObject,
	)

	@Serializable
	private data class GitObject(
		val sha: String,
		val type: String,
	)

	@Serializable
	private data class GitTag(
		val message: String? = null,
	)
}
