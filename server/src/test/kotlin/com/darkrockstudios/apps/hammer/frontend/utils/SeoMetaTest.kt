package com.darkrockstudios.apps.hammer.frontend.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeoMetaTest {

	@Test
	fun `canonical joins base and path`() {
		assertEquals("https://x.test/a/pen/story", buildCanonicalUrl("https://x.test", "/a/pen/story"))
	}

	@Test
	fun `canonical trims a trailing slash on the base and keeps a root path`() {
		assertEquals("https://x.test/", buildCanonicalUrl("https://x.test/", "/"))
	}

	@Test
	fun `canonical adds a missing leading slash`() {
		assertEquals("https://x.test/about", buildCanonicalUrl("https://x.test", "about"))
	}

	@Test
	fun `meta description collapses whitespace`() {
		assertEquals("a b c", metaDescription("  a\n b\t\tc  "))
	}

	@Test
	fun `meta description truncates on a word boundary`() {
		val text = "one two three four five six seven eight nine ten"
		assertEquals("one two three four…", metaDescription(text, maxLength = 20))
	}

	@Test
	fun `meta description passes through when short`() {
		assertEquals("A short bio.", metaDescription("A short bio."))
	}

	@Test
	fun `meta description is null when blank`() {
		assertNull(metaDescription(null))
		assertNull(metaDescription("   \n  "))
	}
}
