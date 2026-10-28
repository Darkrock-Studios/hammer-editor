package repositories.references

import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ImageReferenceSyntaxTest {

	// region findIds

	@Test
	fun `findIds finds every referenced id`() {
		val text = "![a](hammer://image/1)\n\nSee ![b](<hammer://image/12> \"t\") and [c]: hammer://image/7"
		assertEquals(setOf(1, 12, 7), ImageReferenceSyntax.findIds(text))
	}

	@Test
	fun `findIds reads the whole number and nothing that continues into a word`() {
		assertEquals(setOf(12), ImageReferenceSyntax.findIds("![a](hammer://image/12)"))
		assertEquals(emptySet(), ImageReferenceSyntax.findIds("hammer://image/12x"))
		assertEquals(emptySet(), ImageReferenceSyntax.findIds("xhammer://image/3"))
		assertEquals(emptySet(), ImageReferenceSyntax.findIds("hammer://image/99999999999"))
	}

	// endregion

	// region remap

	@Test
	fun `remap rewrites references to a mapped id`() {
		assertEquals(
			"![Map](hammer://image/9)",
			ImageReferenceSyntax.remap("![Map](hammer://image/5)", mapOf(5 to 9)),
		)
	}

	@Test
	fun `remap does not touch a longer id that starts with the mapped one`() {
		val text = "![a](hammer://image/1)\n![b](hammer://image/12)"
		assertEquals(
			"![a](hammer://image/5)\n![b](hammer://image/12)",
			ImageReferenceSyntax.remap(text, mapOf(1 to 5)),
		)
	}

	@Test
	fun `remap applies the whole map in one pass`() {
		val text = "![a](hammer://image/1) ![b](hammer://image/2)"
		assertEquals(
			"![a](hammer://image/2) ![b](hammer://image/3)",
			ImageReferenceSyntax.remap(text, mapOf(1 to 2, 2 to 3)),
		)
	}

	@Test
	fun `remap rewrites the same image every time it appears`() {
		val text = "![a](hammer://image/4)\r\n\r\n![b](<hammer://image/4>)\r\n"
		assertEquals(
			"![a](hammer://image/8)\r\n\r\n![b](<hammer://image/8>)\r\n",
			ImageReferenceSyntax.remap(text, mapOf(4 to 8)),
		)
	}

	@Test
	fun `remap returns the same instance when nothing changes`() {
		val text = "![a](hammer://image/4) and no others"
		assertSame(text, ImageReferenceSyntax.remap(text, mapOf(5 to 6)))
		assertSame(text, ImageReferenceSyntax.remap(text, emptyMap()))
		assertSame(text, ImageReferenceSyntax.remap(text, mapOf(4 to 4)))
	}

	// endregion

	// region remove

	@Test
	fun `remove drops an image line and the doubled blank line it leaves`() {
		assertRemoves("Para one.\n\n![Map](hammer://image/5)\n\nPara two.", "Para one.\n\nPara two.")
	}

	@Test
	fun `remove handles images at the start and the end of the text`() {
		assertRemoves("![x](hammer://image/5)\n\nText", "Text")
		assertRemoves("Text\n\n![x](hammer://image/5)", "Text\n")
		assertRemoves("Text\n\n![x](hammer://image/5)\n", "Text\n")
		assertRemoves("![x](hammer://image/5)", "")
	}

	@Test
	fun `remove keeps blank lines that were already doubled`() {
		assertRemoves("A\n\n![x](hammer://image/5)\n\n\nB", "A\n\n\nB")
	}

	@Test
	fun `remove drops an image inside a paragraph without merging blank lines`() {
		assertRemoves("Line a\n![x](hammer://image/5)\nLine b", "Line a\nLine b")
	}

	@Test
	fun `remove understands the alt text Markdown writers produce`() {
		assertRemoves("A\n\n![A \\[b\\] \\* \\_](hammer://image/5)\n\nB", "A\n\nB")
		assertRemoves("A\n\n![outer [inner] text](hammer://image/5)\n\nB", "A\n\nB")
		assertRemoves("A\n\n![](hammer://image/5)\n\nB", "A\n\nB")
	}

	@Test
	fun `remove understands angle-bracket destinations and titles`() {
		assertRemoves("A\n\n![x](<hammer://image/5>)\n\nB", "A\n\nB")
		assertRemoves("A\n\n![x](hammer://image/5 \"The map\")\n\nB", "A\n\nB")
		assertRemoves("A\n\n![x](hammer://image/5 'The map')\n\nB", "A\n\nB")
		assertRemoves("A\n\n![x](hammer://image/5 (The map))\n\nB", "A\n\nB")
		assertRemoves("A\n\n![x](<hammer://image/5> \"The map\")\n\nB", "A\n\nB")
	}

	@Test
	fun `remove drops an indented image line`() {
		assertRemoves("Text\n\n   ![x](hammer://image/5)  \n\nMore", "Text\n\nMore")
	}

	@Test
	fun `remove drops an image line inside a blockquote`() {
		assertRemoves("> Quote\n>\n> ![x](hammer://image/5)\n>\n> More", "> Quote\n>\n> More")
	}

	@Test
	fun `remove drops a reference definition and the images that use it`() {
		assertRemoves(
			"Look:\n\n![Map][The  Map]\n\nAlso ![the map] inline.\n\n[the map]: hammer://image/5 \"Title\"",
			"Look:\n\nAlso  inline.\n",
		)
	}

	@Test
	fun `remove leaves a longer id that starts with the removed one`() {
		assertRemoves("![a](hammer://image/1)\n\n![b](hammer://image/12)", "![b](hammer://image/12)", imageId = 1)
	}

	@Test
	fun `remove drops every use of the image`() {
		assertRemoves(
			"A\n\n![x](hammer://image/5)\n\nB\n\n![y](hammer://image/5)\n\nC",
			"A\n\nB\n\nC",
		)
		assertRemoves("A\n\n![x](hammer://image/5) ![y](hammer://image/5)\n\nB", "A\n\nB")
	}

	@Test
	fun `remove cuts an inline image out of its line`() {
		assertRemoves("See ![x](hammer://image/5) here.", "See  here.")
	}

	@Test
	fun `remove preserves CRLF line endings`() {
		assertRemoves("A\r\n\r\n![x](hammer://image/5)\r\n\r\nB\r\n", "A\r\n\r\nB\r\n")
	}

	@Test
	fun `remove leaves fenced code alone`() {
		val fenced = "```\n![x](hammer://image/5)\n```\n"
		assertSame(fenced, ImageReferenceSyntax.remove(fenced, 5))

		val tilde = "~~~~md\n![x](hammer://image/5)\n~~~\n~~~~\n"
		assertSame(tilde, ImageReferenceSyntax.remove(tilde, 5))

		assertRemoves(
			"```\n![x](hammer://image/5)\n```\n\n![x](hammer://image/5)\n",
			"```\n![x](hammer://image/5)\n```\n",
		)
	}

	@Test
	fun `remove returns the same instance when nothing changes`() {
		val text = "![a](hammer://image/4)\n\nText"
		assertSame(text, ImageReferenceSyntax.remove(text, 5))
		val link = "A plain [link](hammer://image/5) is not an image."
		assertSame(link, ImageReferenceSyntax.remove(link, 5))
	}

	// endregion

	private fun assertRemoves(text: String, expected: String, imageId: Int = 5) {
		assertEquals(expected, ImageReferenceSyntax.remove(text, imageId))
	}
}
