package com.darkrockstudios.apps.hammer.services

import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LanguageToolConfigTest {

	@Test
	fun `Any pasted form of the address gives the same base`() {
		val bases = listOf(
			"http://languagetool:8010",
			"http://languagetool:8010/",
			"http://languagetool:8010/v2",
			"http://languagetool:8010/v2/",
			" http://languagetool:8010/v2/check ",
		).map { LanguageToolConfig(url = it).baseUrl }

		assertEquals(List(5) { "http://languagetool:8010" }, bases)
	}

	@Test
	fun `Stored limits below their floors are raised`() {
		val config = LanguageToolConfig(requestsPerMinute = 0, maxRequestBytes = -5).withinBounds()

		assertEquals(1, config.requestsPerMinute)
		assertEquals(LanguageToolConfig.MIN_MAX_REQUEST_BYTES, config.maxRequestBytes)
	}

	@Test
	fun `Only absolute http addresses are valid`() {
		val valid = listOf("http://languagetool:8010", " https://lt.example.com/v2 ").map(LanguageToolConfig::validUrl)
		val invalid = listOf("languagetool:8010", "ftp://lt.example.com", "http://", "not a url").map(LanguageToolConfig::validUrl)

		assertEquals(listOf("http://languagetool:8010", "https://lt.example.com/v2"), valid)
		assertEquals(List(4) { null }, invalid)
	}
}
