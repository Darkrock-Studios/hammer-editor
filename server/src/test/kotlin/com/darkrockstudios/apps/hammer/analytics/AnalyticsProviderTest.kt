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
	fun `umami event bridge forwards hammerTrack calls to umami track`() {
		val provider = UmamiAnalyticsProvider(UmamiConfig(websiteId = "x"))
		val bridge = provider.eventBridge()
		assertContains(bridge, "window.hammerTrack")
		assertContains(bridge, "umami.track(n,d)")
	}

	@Test
	fun `umami cloud posts events to the gateway host, not the script host`() {
		// Umami Cloud serves the script from cloud.umami.is but POSTs events to a separate
		// gateway origin (baked into cloud.umami.is/script.js). connect-src must allow it.
		val cloud = UmamiAnalyticsProvider(UmamiConfig(websiteId = "x"))
		assertEquals(listOf("https://cloud.umami.is"), cloud.scriptSrcHosts())
		assertEquals(
			listOf(
				"https://gateway.umami.is",
				"https://eu.umami.is",
				"https://api-gateway.umami.dev",
				"https://api-gateway-eu.umami.dev",
			),
			cloud.connectSrcHosts(),
		)
	}

	@Test
	fun `self-hosted umami posts events to the script origin`() {
		val selfHosted = UmamiAnalyticsProvider(
			UmamiConfig(websiteId = "x", scriptUrl = "https://umami.example.com/script.js")
		)
		assertEquals(listOf("https://umami.example.com"), selfHosted.scriptSrcHosts())
		assertEquals(listOf("https://umami.example.com"), selfHosted.connectSrcHosts())
	}

	@Test
	fun `configured connectSrc overrides the cloud default hosts`() {
		// Escape hatch for when Umami moves the gateway again: config wins over the built-ins.
		val cloud = UmamiAnalyticsProvider(
			UmamiConfig(websiteId = "x", connectSrc = listOf("https://new-gateway.umami.is"))
		)
		assertEquals(listOf("https://new-gateway.umami.is"), cloud.connectSrcHosts())
	}

	@Test
	fun `configured connectSrc overrides the self-hosted script origin`() {
		val selfHosted = UmamiAnalyticsProvider(
			UmamiConfig(
				websiteId = "x",
				scriptUrl = "https://umami.example.com/script.js",
				connectSrc = listOf("https://events.example.com"),
			)
		)
		assertEquals(listOf("https://events.example.com"), selfHosted.connectSrcHosts())
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
