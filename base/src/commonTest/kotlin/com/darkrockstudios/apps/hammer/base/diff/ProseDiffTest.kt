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
	fun `diff on prepared markdown matches the string overload`() {
		val left = "Say **hello** to the world."
		val right = "Say **goodbye** to the world."
		val viaStrings = ProseDiff.diff(left, right)
		val viaPrepared = ProseDiff.diff(ProseDiff.prepare(left), ProseDiff.prepare(right))
		assertEquals(viaStrings, viaPrepared)
	}

	@Test
	fun `diffPlain matches diffing two preparePlain sides`() {
		val left = "The cat sat on the mat."
		val right = "The dog sat on the mat."
		val viaStrings = ProseDiff.diffPlain(left, right)
		val viaPrepared = ProseDiff.diff(ProseDiff.preparePlain(left), ProseDiff.preparePlain(right))
		assertEquals(viaStrings, viaPrepared)
	}

	@Test
	fun `a prepared side can be reused across diffs without affecting results`() {
		val draft = "The quick brown fox."
		val preparedDraft = ProseDiff.preparePlain(draft)

		val first = ProseDiff.diff(preparedDraft, ProseDiff.preparePlain("The quick red fox."))
		val second = ProseDiff.diff(preparedDraft, ProseDiff.preparePlain("The slow brown fox."))

		assertEquals(ProseDiff.diffPlain(draft, "The quick red fox."), first)
		assertEquals(ProseDiff.diffPlain(draft, "The slow brown fox."), second)
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

	@Test
	fun `paragraph moved verbatim is marked moved on both sides`() {
		val left = "Alpha para.\n\nBeta para.\n\nGamma para."
		val right = "Beta para.\n\nGamma para.\n\nAlpha para."
		val result = ProseDiff.diff(left, right)

		val leftMove = result.leftSpans.singleOrNull {
			it.kind == DiffKind.MOVED && "Alpha" in left.substring(it.range.start, it.range.endExclusive)
		}
		val rightMove = result.rightSpans.singleOrNull {
			it.kind == DiffKind.MOVED && "Alpha" in right.substring(it.range.start, it.range.endExclusive)
		}

		assertTrue(leftMove != null, "expected moved Alpha span on left, got: ${result.leftSpans}")
		assertTrue(rightMove != null, "expected moved Alpha span on right, got: ${result.rightSpans}")
		assertEquals(leftMove.moveId, rightMove.moveId)
		assertTrue(result.leftSpans.none { it.kind == DiffKind.DELETED }, "expected no deleted spans, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.none { it.kind == DiffKind.INSERTED }, "expected no inserted spans, got: ${result.rightSpans}")
	}

	@Test
	fun `paragraph moved with whitespace-only change is still marked moved`() {
		val left = "Alpha  para.\n\nBeta para.\n\nGamma para."
		val right = "Beta para.\n\nGamma para.\n\nAlpha para."
		val result = ProseDiff.diff(left, right)

		val leftMove = result.leftSpans.singleOrNull {
			it.kind == DiffKind.MOVED && "Alpha" in left.substring(it.range.start, it.range.endExclusive)
		}
		val rightMove = result.rightSpans.singleOrNull {
			it.kind == DiffKind.MOVED && "Alpha" in right.substring(it.range.start, it.range.endExclusive)
		}

		assertTrue(leftMove != null, "expected moved Alpha span on left, got: ${result.leftSpans}")
		assertTrue(rightMove != null, "expected moved Alpha span on right, got: ${result.rightSpans}")
		assertEquals(leftMove.moveId, rightMove.moveId)
	}

	@Test
	fun `paragraph moved with edited word is not marked moved`() {
		val left = "Alpha para.\n\nBeta para.\n\nGamma para."
		val right = "Beta para.\n\nGamma para.\n\nAlpha changed."
		val result = ProseDiff.diff(left, right)

		assertTrue(result.leftSpans.none { it.kind == DiffKind.MOVED }, "expected no moved left spans, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.none { it.kind == DiffKind.MOVED }, "expected no moved right spans, got: ${result.rightSpans}")
		assertTrue(result.leftSpans.any { it.kind == DiffKind.DELETED }, "expected deleted span, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.any { it.kind == DiffKind.INSERTED }, "expected inserted span, got: ${result.rightSpans}")
	}

	@Test
	fun `duplicate paragraph text is ambiguous and not marked moved`() {
		val left = "Dup para.\n\nKeep one.\n\nDup para.\n\nKeep two."
		val right = "Keep one.\n\nKeep two.\n\nDup para."
		val result = ProseDiff.diff(left, right)

		assertTrue(result.leftSpans.none { it.kind == DiffKind.MOVED }, "expected no moved left spans, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.none { it.kind == DiffKind.MOVED }, "expected no moved right spans, got: ${result.rightSpans}")
		assertTrue(result.leftSpans.any { it.kind == DiffKind.DELETED }, "expected deleted span, got: ${result.leftSpans}")
		assertTrue(result.rightSpans.any { it.kind == DiffKind.INSERTED }, "expected inserted span, got: ${result.rightSpans}")
	}

	@Test
	fun `two different moved paragraphs get distinct move ids`() {
		val left = "Alpha para.\n\nStable one.\n\nBeta para.\n\nStable two.\n\nStable three."
		val right = "Stable one.\n\nAlpha para.\n\nStable two.\n\nBeta para.\n\nStable three."
		val result = ProseDiff.diff(left, right)

		val movedSpans = result.leftSpans.plus(result.rightSpans).filter { it.kind == DiffKind.MOVED }
		val movedIds = movedSpans.mapNotNull { it.moveId }.toSet()

		assertEquals(4, movedSpans.size, "expected two moved pairs, got: $movedSpans")
		assertEquals(2, movedIds.size, "expected two distinct move ids, got: $movedIds")
	}

	@Test
	fun `moved hunk emits no scroll sync anchor inside moved paragraph`() {
		val left = "Alpha para.\n\nBeta para.\n\nGamma para."
		val right = "Beta para.\n\nGamma para.\n\nAlpha para."
		val result = ProseDiff.diff(left, right)
		val movedLeft = result.leftSpans.single {
			it.kind == DiffKind.MOVED && "Alpha" in left.substring(it.range.start, it.range.endExclusive)
		}

		assertEquals(DiffAnchor(0, 0), result.anchors.first())
		assertEquals(DiffAnchor(left.length, right.length), result.anchors.last())

		var prev = result.anchors.first()
		for (a in result.anchors.drop(1)) {
			assertTrue(a.leftSource >= prev.leftSource, "left anchors must be non-decreasing: $prev -> $a")
			assertTrue(a.rightSource >= prev.rightSource, "right anchors must be non-decreasing: $prev -> $a")
			prev = a
		}

		val movedRange = movedLeft.range
		assertTrue(
			result.anchors.none { it.leftSource > movedRange.start && it.leftSource < movedRange.endExclusive },
			"expected no left anchor inside moved range $movedRange, got: ${result.anchors}",
		)
	}
}
