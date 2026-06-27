package com.darkrockstudios.apps.hammer.base.diff

/** A half-open range `[start, endExclusive)` into a source string. */
data class SourceRange(val start: Int, val endExclusive: Int) {
	val length: Int get() = endExclusive - start
	val isEmpty: Boolean get() = endExclusive <= start

	companion object {
		val EMPTY = SourceRange(0, 0)
	}
}

enum class DiffKind { DELETED, INSERTED, MOVED }

/** One change span to highlight in either the left (old) or right (new) text. */
data class DiffSpan(
	val kind: DiffKind,
	val range: SourceRange,
	val moveId: Int? = null,
)

/**
 * A pair of corresponding offsets in the left and right source texts.
 *
 * Anchors sit at the start and end of every delta and at the document edges, so a translator
 * can binary-search anchors and interpolate (left ↔ right lengths are identical between adjacent
 * anchors when crossing equal regions, and proportional inside an edit hunk).
 */
data class DiffAnchor(val leftSource: Int, val rightSource: Int)

data class DiffResult(
	val leftSpans: List<DiffSpan>,
	val rightSpans: List<DiffSpan>,
	val anchors: List<DiffAnchor>,
) {
	companion object {
		val EMPTY = DiffResult(emptyList(), emptyList(), listOf(DiffAnchor(0, 0)))
	}
}
