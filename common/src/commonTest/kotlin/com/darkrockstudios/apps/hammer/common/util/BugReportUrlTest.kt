package com.darkrockstudios.apps.hammer.common.util

import io.ktor.http.decodeURLQueryComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BugReportUrlTest {

	private val repo = "https://github.com/Darkrock-Studios/hammer-editor/"

	@Test
	fun buildsNewIssueUrlAgainstRepo() {
		val url = buildBugReportUrl(
			appVersion = "v1.2.3",
			platformName = "Desktop",
			platformInfo = "OS: Linux",
			repoUrl = repo,
		)
		assertTrue(
			url.startsWith("https://github.com/Darkrock-Studios/hammer-editor/issues/new?"),
			"Unexpected URL: $url",
		)
	}

	@Test
	fun handlesTrailingSlashInRepoUrl() {
		val withSlash = buildBugReportUrl("v1", "Desktop", "info", "https://example.com/repo/")
		val withoutSlash = buildBugReportUrl("v1", "Desktop", "info", "https://example.com/repo")
		assertTrue(withSlash.startsWith("https://example.com/repo/issues/new?"))
		assertTrue(withoutSlash.startsWith("https://example.com/repo/issues/new?"))
	}

	@Test
	fun setsBugLabel() {
		val url = buildBugReportUrl("v1.2.3", "Android", "OS: Android 14", repo)
		assertEquals("bug", queryParam(url, "labels"))
	}

	@Test
	fun bodyContainsEnvironmentDetails() {
		val url = buildBugReportUrl(
			appVersion = "v1.2.3",
			platformName = "Android",
			platformInfo = "OS: Android 14 (API 34)",
			repoUrl = repo,
		)
		val body = queryParam(url, "body") ?: error("no body param")
		assertTrue(body.contains("v1.2.3"), "body missing version: $body")
		assertTrue(body.contains("Android"), "body missing platform: $body")
		assertTrue(body.contains("OS: Android 14 (API 34)"), "body missing platform info: $body")
	}

	@Test
	fun encodesSpecialCharactersInBody() {
		// A platformInfo containing & and spaces must survive the round-trip intact,
		// proving the query value is properly percent-encoded.
		val info = "OS: Windows 11 & display: session/GNOME"
		val url = buildBugReportUrl("v1.2.3", "Desktop", info, repo)
		val body = queryParam(url, "body") ?: error("no body param")
		assertTrue(body.contains(info), "special chars not preserved: $body")
	}

	private fun queryParam(url: String, name: String): String? {
		// Decode manually rather than via Url.parameters so a "+"-encoded space
		// is handled the same way GitHub decodes it.
		val query = url.substringAfter('?', "")
		return query.split('&')
			.map { it.substringBefore('=') to it.substringAfter('=', "") }
			.firstOrNull { it.first == name }
			?.second
			?.decodeURLQueryComponent(plusIsSpace = true)
	}
}
