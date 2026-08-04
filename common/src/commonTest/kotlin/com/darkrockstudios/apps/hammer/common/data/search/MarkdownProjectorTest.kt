package com.darkrockstudios.apps.hammer.common.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownProjectorTest {

	private val projector = MarkdownProjector()

	@Test
	fun `a reused projector gives the same answer as a fresh one`() {
		val documents = listOf(
			"The **big** dog barked.",
			"a much longer document ".repeat(200) + "with *emphasis* at the end",
			"short",
			"# Heading\n\nBody text.",
		)
		documents.forEach { document ->
			projector.project(document)
			assertEquals(MarkdownProjector().also { it.project(document) }.projected(), projector.projected())
		}
	}

	@Test
	fun `a long document followed by a short one does not leak the tail of the long one`() {
		projector.project("the quick brown fox ".repeat(100))
		projector.project("short")
		assertEquals("short", projector.projected())
	}

	@Test
	fun `indexOf finds a phrase that spans dropped emphasis`() {
		projector.project("the **big** dog")
		assertEquals("the big dog", projector.projected())
		assertEquals(4, projector.indexOf("big dog"))
	}

	@Test
	fun `indexOf is case insensitive by default and reports a miss as -1`() {
		projector.project("The Harbour At Dawn")
		assertEquals(4, projector.indexOf("harbour"))
		assertEquals(-1, projector.indexOf("mountain"))
	}

	@Test
	fun `indexOf respects case when asked`() {
		projector.project("The Harbour")
		assertEquals(-1, projector.indexOf("harbour", ignoreCase = false))
		assertEquals(4, projector.indexOf("Harbour", ignoreCase = false))
	}

	@Test
	fun `an empty query never matches`() {
		projector.project("anything")
		assertEquals(-1, projector.indexOf(""))
	}

	@Test
	fun `a query longer than the document never matches`() {
		projector.project("hi")
		assertEquals(-1, projector.indexOf("hello there"))
	}

	@Test
	fun `substring returns the projected prose, not the source`() {
		projector.project("the **big** dog")
		assertEquals("big", projector.substring(4, 7))
	}

	@Test
	fun `firstNonBlankLine skips blank lines and strips markers`() {
		projector.project("\n\n# **Chapter** One\n\nBody")
		assertEquals("Chapter One", projector.firstNonBlankLine())
	}

	@Test
	fun `firstNonBlankLine is empty when every line is blank`() {
		projector.project("\n   \n\t\n")
		assertEquals("", projector.firstNonBlankLine())
	}

	@Test
	fun `collapsedPreview flattens whitespace runs`() {
		projector.project("the   quick\n\nbrown\tfox")
		assertEquals("the quick brown fox", projector.collapsedPreview(100))
	}

	@Test
	fun `collapsedPreview truncates with an ellipsis and stays within the cap`() {
		projector.project("word ".repeat(100))
		val preview = projector.collapsedPreview(20)
		assertTrue(preview.endsWith("…"), "expected an ellipsis, got: $preview")
		// The cap bounds the prose; trimming a trailing space before the ellipsis can leave it shorter.
		assertTrue(preview.length <= 21, "expected at most 21 chars, got ${preview.length}: $preview")
		assertEquals("word word word word…", preview)
	}

	@Test
	fun `collapsedPreview does not add an ellipsis when the whole text fits`() {
		projector.project("just this")
		assertEquals("just this", projector.collapsedPreview(50))
	}

	@Test
	fun `trailing whitespace does not count as more text to preview`() {
		projector.project("just this   \n\n  ")
		assertEquals("just this", projector.collapsedPreview(50))
	}

	@Test
	fun `filling the source buffer directly projects the same as passing a string`() {
		val document = "the **big** dog barked at the well\\-known man"
		projector.project(document)
		val viaString = projector.projected()

		val buffer = projector.sourceBuffer(document.length)
		document.forEachIndexed { i, c -> buffer[i] = c }
		projector.projectSource(document.length)

		assertEquals(viaString, projector.projected())
	}

	@Test
	fun `the source buffer survives being grown for a longer document`() {
		val long = "a *lot* of text ".repeat(50)
		val buffer = projector.sourceBuffer(long.length)
		long.forEachIndexed { i, c -> buffer[i] = c }
		projector.projectSource(long.length)
		assertEquals(projectMarkdownToPlainText(long), projector.projected())
	}
}
