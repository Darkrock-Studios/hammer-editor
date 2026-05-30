package com.darkrockstudios.apps.hammer.base.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextExtractorTest {

	@Test
	fun `empty markdown returns empty plain text`() {
		val result = extractPlainText("")
		assertEquals("", result.plain)
		assertTrue(result.segments.isEmpty())
	}

	@Test
	fun `plain prose passes through unchanged`() {
		val md = "Hello world. The cat sat on the mat."
		val result = extractPlainText(md)
		assertEquals(md, result.plain)
	}

	@Test
	fun `bold markers are stripped but the word survives`() {
		val result = extractPlainText("**bold** word")
		assertTrue("bold" in result.plain)
		assertTrue("word" in result.plain)
		assertTrue("**" !in result.plain)
	}

	@Test
	fun `atx heading marker is stripped`() {
		val result = extractPlainText("# Heading")
		assertTrue("Heading" in result.plain)
		assertTrue("#" !in result.plain)
	}

	@Test
	fun `list bullets are stripped`() {
		val result = extractPlainText("- one\n- two")
		assertTrue("one" in result.plain)
		assertTrue("two" in result.plain)
		// Neither bullet character should remain.
		assertEquals(0, result.plain.count { it == '-' })
	}

	@Test
	fun `plain offset round-trips through source offset for a clean text`() {
		val md = "The quick brown fox."
		val result = extractPlainText(md)
		// In a no-syntax document every plain offset must map back to itself.
		for (i in 0..md.length) {
			assertEquals(i, result.plainOffsetToSource(i), "plain offset $i should map to source $i")
		}
	}

	@Test
	fun `plain range to source covers the bold markers when spanning them`() {
		val md = "say **hi** there"
		// plain becomes "say hi there"; offsets shift by ** length.
		val result = extractPlainText(md)
		val plainHi = result.plain.indexOf("hi")
		val range = result.plainRangeToSource(plainHi, plainHi + 2)
		// Source range must include the literal "hi" (could also include surrounding markers).
		val sourceHi = md.indexOf("hi")
		assertTrue(range.start <= sourceHi, "range start $range should be <= source position of 'hi' ($sourceHi)")
		assertTrue(range.endExclusive >= sourceHi + 2, "range end $range should be >= ${sourceHi + 2}")
	}

	@Test
	fun `segments do not overlap and are strictly ordered`() {
		val md = "# Title\n\n**Bold** and *italic* prose.\n\n- a\n- b"
		val result = extractPlainText(md)
		var prevPlainEnd = 0
		var prevSourceEnd = 0
		for (seg in result.segments) {
			assertTrue(seg.plainStart >= prevPlainEnd, "plain segments must be ordered: $seg after end=$prevPlainEnd")
			assertTrue(seg.sourceStart >= prevSourceEnd, "source segments must be ordered: $seg after end=$prevSourceEnd")
			assertTrue(seg.plainEnd > seg.plainStart, "empty segment leaked: $seg")
			assertEquals(seg.plainEnd - seg.plainStart, seg.sourceEnd - seg.sourceStart, "segment widths must match")
			prevPlainEnd = seg.plainEnd
			prevSourceEnd = seg.sourceEnd
		}
	}
}
