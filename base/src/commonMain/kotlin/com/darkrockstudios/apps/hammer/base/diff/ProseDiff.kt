package com.darkrockstudios.apps.hammer.base.diff

import io.github.petertrr.diffutils.diff

/**
 * Compute a word-level diff between two markdown texts, returning highlight spans for each
 * side and anchor pairs for synchronized scrolling.
 *
 * Pipeline:
 *  1. Strip markdown to plain text, keeping an offset map back to source.
 *  2. Tokenize plain text into word / whitespace / punctuation runs.
 *  3. Run Myers diff over the token sequences.
 *  4. Translate each delta into plain-text ranges and merge across short common runs so
 *     "the cat sat" → "the dog ran" reads as one change rather than two.
 *  5. Translate plain-text ranges back into source ranges for the UI to highlight.
 *
 * Anchors mark every delta edge in both source coordinate spaces; the caller interpolates
 * between them to map an arbitrary left offset to its right counterpart.
 */
object ProseDiff {

	/** Threshold (in plain-text chars) for merging adjacent edits separated by a short common run. */
	private const val SMALL_GAP_THRESHOLD = 3

	fun diff(leftMarkdown: String, rightMarkdown: String): DiffResult {
		if (leftMarkdown == rightMarkdown) {
			return DiffResult(
				leftSpans = emptyList(),
				rightSpans = emptyList(),
				anchors = listOf(
					DiffAnchor(0, 0),
					DiffAnchor(leftMarkdown.length, rightMarkdown.length),
				),
			)
		}

		val left = extractPlainText(leftMarkdown)
		val right = extractPlainText(rightMarkdown)
		val leftTokens = tokenize(left.plain)
		val rightTokens = tokenize(right.plain)

		val patch = diff(
			source = leftTokens.map { it.text },
			target = rightTokens.map { it.text },
		)

		val rawHunks = patch.deltas.map { delta ->
			val srcPos = delta.source.position
			val srcEndExclusive = srcPos + delta.source.lines.size
			val tgtPos = delta.target.position
			val tgtEndExclusive = tgtPos + delta.target.lines.size
			Hunk(
				leftPlainStart = tokenStartPlain(leftTokens, srcPos, left.plain.length),
				leftPlainEnd = tokenEndPlain(leftTokens, srcEndExclusive),
				rightPlainStart = tokenStartPlain(rightTokens, tgtPos, right.plain.length),
				rightPlainEnd = tokenEndPlain(rightTokens, tgtEndExclusive),
			)
		}

		val merged = mergeSmallGaps(rawHunks, SMALL_GAP_THRESHOLD)

		val leftSpans = ArrayList<DiffSpan>(merged.size)
		val rightSpans = ArrayList<DiffSpan>(merged.size)
		val anchors = ArrayList<DiffAnchor>(merged.size * 2 + 2)
		anchors += DiffAnchor(0, 0)

		for (h in merged) {
			anchors += DiffAnchor(
				leftSource = left.plainOffsetToSource(h.leftPlainStart),
				rightSource = right.plainOffsetToSource(h.rightPlainStart),
			)
			if (h.leftPlainEnd > h.leftPlainStart) {
				val range = left.plainRangeToSource(h.leftPlainStart, h.leftPlainEnd)
				if (!range.isEmpty) leftSpans += DiffSpan(DiffKind.DELETED, range)
			}
			if (h.rightPlainEnd > h.rightPlainStart) {
				val range = right.plainRangeToSource(h.rightPlainStart, h.rightPlainEnd)
				if (!range.isEmpty) rightSpans += DiffSpan(DiffKind.INSERTED, range)
			}
			anchors += DiffAnchor(
				leftSource = left.plainOffsetToSource(h.leftPlainEnd, preferEnd = true),
				rightSource = right.plainOffsetToSource(h.rightPlainEnd, preferEnd = true),
			)
		}

		anchors += DiffAnchor(leftMarkdown.length, rightMarkdown.length)

		return DiffResult(
			leftSpans = leftSpans,
			rightSpans = rightSpans,
			anchors = anchors.distinct(),
		)
	}
}

private data class Hunk(
	val leftPlainStart: Int,
	val leftPlainEnd: Int,
	val rightPlainStart: Int,
	val rightPlainEnd: Int,
)

private fun tokenStartPlain(tokens: List<Token>, tokenIndex: Int, fallback: Int): Int =
	tokens.getOrNull(tokenIndex)?.plainStart ?: fallback

private fun tokenEndPlain(tokens: List<Token>, tokenIndexExclusive: Int): Int =
	if (tokenIndexExclusive <= 0) 0
	else tokens.getOrNull(tokenIndexExclusive - 1)?.plainEnd
		?: tokens.lastOrNull()?.plainEnd
		?: 0

private fun mergeSmallGaps(hunks: List<Hunk>, gapThreshold: Int): List<Hunk> {
	if (hunks.size < 2) return hunks
	val out = ArrayList<Hunk>(hunks.size)
	var current = hunks[0]
	for (i in 1 until hunks.size) {
		val next = hunks[i]
		val leftGap = next.leftPlainStart - current.leftPlainEnd
		val rightGap = next.rightPlainStart - current.rightPlainEnd
		if (leftGap in 0..gapThreshold && rightGap in 0..gapThreshold) {
			current = current.copy(
				leftPlainEnd = next.leftPlainEnd,
				rightPlainEnd = next.rightPlainEnd,
			)
		} else {
			out += current
			current = next
		}
	}
	out += current
	return out
}
