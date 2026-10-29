package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HtmlEscapeTest {
	@Test
	fun `escapes markup and double quotes`() {
		assertEquals("&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&lt;/a&gt;", escapeHtml("<a href=\"x\">Tom & Jerry</a>"))
	}

	@Test
	fun `escapes an existing entity again`() {
		assertEquals("&amp;amp;", escapeHtml("&amp;"))
	}

	@Test
	fun `leaves single quotes alone`() {
		assertEquals("it's", escapeHtml("it's"))
	}
}
