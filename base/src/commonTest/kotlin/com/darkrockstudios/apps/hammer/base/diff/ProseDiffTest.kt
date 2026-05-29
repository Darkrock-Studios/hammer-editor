package com.darkrockstudios.apps.hammer.base.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProseDiffTest {

	@Test
	fun `identical text produces no spans`() {
		val text = "The cat sat on the mat."
		val result = ProseDiff.diff(text, text)
		assertTrue(result.leftSpans.isEmpty())
		assertTrue(result.rightSpans.isEmpty())
	}

	@Test
	fun `single word replacement is highlighted on both sides`() {
		val left = "The cat sat on the mat."
		val right = "The dog sat on the mat."
		val result = ProseDiff.diff(left, right)
		assertEquals(1, result.leftSpans.size, "expected one DELETED span on left, got: ${result.leftSpans}")
		assertEquals(1, result.rightSpans.size, "expected one INSERTED span on right, got: ${result.rightSpans}")

		val leftRange = result.leftSpans.single().range
		val rightRange = result.rightSpans.single().range
		assertEquals("cat", left.substring(leftRange.start, leftRange.endExclusive))
		assertEquals("dog", right.substring(rightRange.start, rightRange.endExclusive))
	}

	@Test
	fun `pure insertion produces only right-side span`() {
		val left = "The cat sat."
		val right = "The big cat sat."
		val result = ProseDiff.diff(left, right)
		assertTrue(result.leftSpans.isEmpty(), "expected no left spans, got: ${result.leftSpans}")
		assertEquals(1, result.rightSpans.size)
		val r = result.rightSpans.single().range
		val highlighted = right.substring(r.start, r.endExclusive)
		assertTrue("big" in highlighted, "inserted span '$highlighted' should contain 'big'")
	}

	@Test
	fun `pure deletion produces only left-side span`() {
		val left = "The big cat sat."
		val right = "The cat sat."
		val result = ProseDiff.diff(left, right)
		assertEquals(1, result.leftSpans.size)
		assertTrue(result.rightSpans.isEmpty(), "expected no right spans, got: ${result.rightSpans}")
		val l = result.leftSpans.single().range
		assertTrue("big" in left.substring(l.start, l.endExclusive))
	}

	@Test
	fun `adjacent changes separated by a short common run get merged into one hunk`() {
		val left = "the cat sat"
		val right = "the dog ran"
		val result = ProseDiff.diff(left, right)
		// Without merge this would be two CHANGE deltas; with the threshold-3 merge it collapses to one.
		assertEquals(1, result.leftSpans.size, "expected merged into 1 left span, got: ${result.leftSpans}")
		assertEquals(1, result.rightSpans.size, "expected merged into 1 right span, got: ${result.rightSpans}")
		val l = left.substring(result.leftSpans.single().range.start, result.leftSpans.single().range.endExclusive)
		val r = right.substring(result.rightSpans.single().range.start, result.rightSpans.single().range.endExclusive)
		assertEquals("cat sat", l)
		assertEquals("dog ran", r)
	}

	@Test
	fun `changes separated by a long common run stay as separate hunks`() {
		val left = "alpha quick brown fox lazy delta"
		val right = "beta quick brown fox lazy gamma"
		val result = ProseDiff.diff(left, right)
		assertEquals(2, result.leftSpans.size, "expected two separate left spans, got: ${result.leftSpans}")
		assertEquals(2, result.rightSpans.size, "expected two separate right spans, got: ${result.rightSpans}")
	}

	@Test
	fun `anchors begin at zero and end at document lengths`() {
		val left = "one two three"
		val right = "one four three"
		val result = ProseDiff.diff(left, right)
		assertEquals(DiffAnchor(0, 0), result.anchors.first())
		assertEquals(DiffAnchor(left.length, right.length), result.anchors.last())
	}

	@Test
	fun `anchors are monotonically non-decreasing on both axes`() {
		val left = "alpha bravo charlie delta echo foxtrot"
		val right = "alpha zulu charlie yankee echo whiskey"
		val result = ProseDiff.diff(left, right)
		var prev = result.anchors.first()
		for (a in result.anchors.drop(1)) {
			assertTrue(a.leftSource >= prev.leftSource, "left anchors must be non-decreasing: $prev -> $a")
			assertTrue(a.rightSource >= prev.rightSource, "right anchors must be non-decreasing: $prev -> $a")
			prev = a
		}
	}

	@Test
	fun `markdown formatting changes are invisible at the prose level`() {
		// Same prose, different emphasis: no diff spans expected because we diff stripped text.
		val left = "Hello world."
		val right = "**Hello** world."
		val result = ProseDiff.diff(left, right)
		assertTrue(result.leftSpans.isEmpty(), "formatting-only changes should not produce left spans, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.isEmpty(), "formatting-only changes should not produce right spans, got: ${result.rightSpans}")
	}

	@Test
	fun `word change inside bold survives markdown stripping with usable source ranges`() {
		val left = "Say **hello** to the world."
		val right = "Say **goodbye** to the world."
		val result = ProseDiff.diff(left, right)
		assertEquals(1, result.leftSpans.size)
		assertEquals(1, result.rightSpans.size)
		val leftHighlight = left.substring(result.leftSpans.single().range.start, result.leftSpans.single().range.endExclusive)
		val rightHighlight = right.substring(result.rightSpans.single().range.start, result.rightSpans.single().range.endExclusive)
		assertTrue("hello" in leftHighlight, "expected 'hello' inside left highlight '$leftHighlight'")
		assertTrue("goodbye" in rightHighlight, "expected 'goodbye' inside right highlight '$rightHighlight'")
	}

	@Test
	fun `diffPlain returns spans in the input text's own coordinates`() {
		// No markdown stripping: offsets index directly into the given strings.
		val left = "The cat sat on the mat."
		val right = "The dog sat on the mat."
		val result = ProseDiff.diffPlain(left, right)
		val l = result.leftSpans.single().range
		val r = result.rightSpans.single().range
		assertEquals("cat", left.substring(l.start, l.endExclusive))
		assertEquals("dog", right.substring(r.start, r.endExclusive))
	}

	@Test
	fun `diffPlain does not strip markdown so syntax changes are visible`() {
		// Unlike diff(), diffPlain treats the asterisks as ordinary characters.
		val left = "Hello world."
		val right = "**Hello** world."
		val result = ProseDiff.diffPlain(left, right)
		assertTrue(result.rightSpans.isNotEmpty(), "diffPlain should see the added ** as a change")
	}

	@Test
	fun `diffPlain on identical text yields no spans`() {
		val text = "Nothing changed here."
		val result = ProseDiff.diffPlain(text, text)
		assertTrue(result.leftSpans.isEmpty())
		assertTrue(result.rightSpans.isEmpty())
	}

	@Test
	fun `paragraph insertion is highlighted as one block`() {
		val left = "Para one.\n\nPara three."
		val right = "Para one.\n\nPara two.\n\nPara three."
		val result = ProseDiff.diff(left, right)
		assertTrue(result.leftSpans.isEmpty(), "pure insertion should leave left unchanged, got: ${result.leftSpans}")
		assertEquals(1, result.rightSpans.size, "expected 1 right span for paragraph insertion, got: ${result.rightSpans}")
		val r = result.rightSpans.single().range
		val highlighted = right.substring(r.start, r.endExclusive)
		assertTrue("Para two" in highlighted, "expected 'Para two' in inserted highlight, got: '$highlighted'")
	}
}
