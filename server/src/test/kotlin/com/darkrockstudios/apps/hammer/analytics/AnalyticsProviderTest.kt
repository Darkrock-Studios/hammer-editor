package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.UmamiConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AnalyticsProviderTest {

	@Test
	fun `umami head snippet contains the website id and script url`() {
		val provider = UmamiAnalyticsProvider(UmamiConfig(websiteId = "abc-123"))
		val snippet = provider.headSnippet()
		assertContains(snippet, "defer")
		assertContains(snippet, """data-website-id="abc-123"""")
		assertContains(snippet, """src="https://cloud.umami.is/script.js"""")
	}

	@Test
	fun `umami head snippet escapes quotes in attribute values`() {
		val provider = UmamiAnalyticsProvider(UmamiConfig(websiteId = """a"b"""))
		val snippet = provider.headSnippet()
		assertContains(snippet, "data-website-id=\"a&quot;b\"")
	}

	@Test
	fun `umami csp hosts use the script origin only`() {
		val cloud = UmamiAnalyticsProvider(UmamiConfig(websiteId = "x"))
		assertEquals(listOf("https://cloud.umami.is"), cloud.scriptSrcHosts())
		assertEquals(listOf("https://cloud.umami.is"), cloud.connectSrcHosts())

		val selfHosted = UmamiAnalyticsProvider(
			UmamiConfig(websiteId = "x", scriptUrl = "https://umami.example.com/script.js")
		)
		assertEquals(listOf("https://umami.example.com"), selfHosted.scriptSrcHosts())
	}

	@Test
	fun `originOf keeps an explicit port and strips the path`() {
		assertEquals("https://umami.example.com:3000", originOf("https://umami.example.com:3000/script.js"))
	}

	@Test
	fun `factory returns null when analytics disabled`() {
		assertNull(AnalyticsProviderFactory.create(AnalyticsConfig(type = AnalyticsProviderType.NONE)))
	}

	@Test
	fun `factory returns null for umami type with no config block`() {
		assertNull(AnalyticsProviderFactory.create(AnalyticsConfig(type = AnalyticsProviderType.UMAMI, umami = null)))
	}

	@Test
	fun `factory returns a umami provider when configured`() {
		val provider = AnalyticsProviderFactory.create(
			AnalyticsConfig(type = AnalyticsProviderType.UMAMI, umami = UmamiConfig(websiteId = "x"))
		)
		assertIs<UmamiAnalyticsProvider>(provider)
	}
}
