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
		val withSlash = buildBugReportUrl("v1", "Desktop", "info", repoUrl = "https://example.com/repo/")
		val withoutSlash = buildBugReportUrl("v1", "Desktop", "info", repoUrl = "https://example.com/repo")
		assertTrue(withSlash.startsWith("https://example.com/repo/issues/new?"))
		assertTrue(withoutSlash.startsWith("https://example.com/repo/issues/new?"))
	}

	@Test
	fun setsBugLabel() {
		val url = buildBugReportUrl("v1.2.3", "Android", "OS: Android 14", repoUrl = repo)
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
	fun embedsCrashTraceWhenProvided() {
		val url = buildBugReportUrl(
			appVersion = "v1.2.3",
			platformName = "Desktop",
			platformInfo = "OS: Linux",
			crashContent = "java.lang.IllegalStateException: boom\n\tat com.example.Thing.run(Thing.kt:42)",
			repoUrl = repo,
		)
		val body = queryParam(url, "body") ?: error("no body param")
		assertTrue(body.contains("Most recent crash"), "missing crash section: $body")
		assertTrue(body.contains("java.lang.IllegalStateException: boom"), "missing trace: $body")
		assertTrue(body.contains("Thing.kt:42"), "missing frame: $body")
	}

	@Test
	fun omitsCrashSectionWhenNullOrBlank() {
		val none = queryParam(buildBugReportUrl("v1", "Desktop", "info", crashContent = null, repoUrl = repo), "body")!!
		val blank = queryParam(buildBugReportUrl("v1", "Desktop", "info", crashContent = "   ", repoUrl = repo), "body")!!
		assertTrue(!none.contains("Most recent crash"), "crash section should be absent: $none")
		assertTrue(!blank.contains("Most recent crash"), "crash section should be absent for blank: $blank")
	}

	@Test
	fun truncatesOversizedCrashTrace() {
		val huge = "x".repeat(20_000)
		val url = buildBugReportUrl("v1", "Desktop", "info", crashContent = huge, repoUrl = repo)
		val body = queryParam(url, "body") ?: error("no body param")
		assertTrue(body.contains("truncated"), "expected a truncation marker: ${body.take(200)}…")
		assertTrue(body.length < huge.length, "body should be shorter than the raw crash")
	}

	@Test
	fun encodesSpecialCharactersInBody() {
		// A platformInfo containing & and spaces must survive the round-trip intact,
		// proving the query value is properly percent-encoded.
		val info = "OS: Windows 11 & display: session/GNOME"
		val url = buildBugReportUrl("v1.2.3", "Desktop", info, repoUrl = repo)
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
