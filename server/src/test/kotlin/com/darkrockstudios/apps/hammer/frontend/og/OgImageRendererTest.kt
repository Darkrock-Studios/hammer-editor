package com.darkrockstudios.apps.hammer.frontend.og

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OgImageRendererTest {

	/** One pixel per character, so wrapping is deterministic and AWT-free. */
	private val charWidth: (String) -> Int = { it.length }

	@Test
	fun `wraps words across lines`() {
		assertEquals(
			listOf("aaa bbb", "ccc"),
			wrapText("aaa bbb ccc", maxWidth = 7, maxLines = 3, charWidth),
		)
	}

	@Test
	fun `short text stays on a single line`() {
		assertEquals(listOf("hi there"), wrapText("hi there", maxWidth = 100, maxLines = 3, charWidth))
	}

	@Test
	fun `truncates with an ellipsis past the line limit`() {
		val lines = wrapText("aaa bbb ccc ddd eee", maxWidth = 7, maxLines = 1, charWidth)
		assertEquals(1, lines.size)
		assertTrue(lines.single().endsWith("…"), "expected ellipsis, got ${lines.single()}")
		assertTrue(charWidth(lines.single()) <= 7)
	}

	@Test
	fun `a single overflowing word gets its own line`() {
		assertEquals(listOf("supercalifragilistic"), wrapText("supercalifragilistic", maxWidth = 5, maxLines = 3, charWidth))
	}

	@Test
	fun `blank text yields no lines`() {
		assertEquals(emptyList(), wrapText("   \n ", maxWidth = 100, maxLines = 3, charWidth))
	}

	@Test
	fun `renders a valid png of the expected dimensions`() {
		val bytes = OgImageRenderer().render("A Reasonably Long Story Title That Wraps", "by Jane Doe")
		val decoded = ImageIO.read(ByteArrayInputStream(bytes))
		assertNotNull(decoded, "rendered bytes were not a decodable image")
		assertEquals(1200, decoded.width)
		assertEquals(630, decoded.height)
	}
}
