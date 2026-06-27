package com.darkrockstudios.apps.hammer.base.diff

import io.github.petertrr.diffutils.diff

/**
 * A text whose markdown has been stripped and tokenized, ready to be diffed. Preparing the
 * immutable side of a comparison once and reusing it across recomputes avoids re-stripping and
 * re-tokenizing it on every edit of the other side. Opaque to callers — build one with
 * [ProseDiff.prepare] (markdown) or [ProseDiff.preparePlain] (already-plain text).
 */
class PreparedText internal constructor(
	internal val plain: PlainTextResult,
	internal val tokens: List<Token>,
	internal val sourceLength: Int,
)

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

	/** Strip markdown to plain text (keeping the offset map back to source) and tokenize. */
	fun prepare(markdown: String): PreparedText {
		val plain = extractPlainText(markdown)
		return PreparedText(plain, tokenize(plain.plain), markdown.length)
	}

	/** Tokenize an already-plain text with an identity offset map (no markdown stripping). */
	fun preparePlain(text: String): PreparedText {
		val plain = identityPlainText(text)
		return PreparedText(plain, tokenize(plain.plain), text.length)
	}

	/**
	 * Diff two markdown texts. Spans are in *markdown source* coordinates — use this when the UI
	 * displays the raw markdown (e.g. the conflict-merge plain text fields).
	 */
	fun diff(leftMarkdown: String, rightMarkdown: String): DiffResult =
		diff(prepare(leftMarkdown), prepare(rightMarkdown))

	/**
	 * Diff two already-plain texts. Spans are in the input strings' own coordinates — use this
	 * when the UI displays the same text being diffed (e.g. the rendered text inside the markdown
	 * editor, where markdown syntax has already been stripped away).
	 */
	fun diffPlain(left: String, right: String): DiffResult =
		diff(preparePlain(left), preparePlain(right))

	/**
	 * Diff two [PreparedText]s. Spans are in the source coordinates each side was prepared from —
	 * both sides must be prepared the same way ([prepare] vs [preparePlain]).
	 */
	fun diff(left: PreparedText, right: PreparedText): DiffResult {
		if (left.plain.plain == right.plain.plain) {
			return DiffResult(
				leftSpans = emptyList(),
				rightSpans = emptyList(),
				anchors = listOf(
					DiffAnchor(0, 0),
					DiffAnchor(left.sourceLength, right.sourceLength),
				),
			)
		}

		val leftTokens = left.tokens
		val rightTokens = right.tokens

		val patch = diff(
			source = leftTokens.map { it.text },
			target = rightTokens.map { it.text },
		)

		val rawHunks = patch.deltas.map { delta ->
			val srcLen = delta.source.lines.size
			val tgtLen = delta.target.lines.size
			val (srcPos, tgtPos) = slideToBoundary(
				delta.source.position, srcLen, delta.target.position, tgtLen, leftTokens, rightTokens,
			)
			val srcEndExclusive = srcPos + srcLen
			val tgtEndExclusive = tgtPos + tgtLen
			Hunk(
				leftPlainStart = tokenStartPlain(leftTokens, srcPos, left.plain.plain.length),
				leftPlainEnd = tokenEndPlain(leftTokens, srcEndExclusive),
				rightPlainStart = tokenStartPlain(rightTokens, tgtPos, right.plain.plain.length),
				rightPlainEnd = tokenEndPlain(rightTokens, tgtEndExclusive),
			)
		}

		val merged = mergeSmallGaps(rawHunks, SMALL_GAP_THRESHOLD)
		val movePlan = detectMovedParagraphs(left.plain.plain, right.plain.plain)

		val leftSpans = ArrayList<DiffSpan>(merged.size)
		val rightSpans = ArrayList<DiffSpan>(merged.size)
		val anchors = ArrayList<DiffAnchor>(merged.size * 2 + 2)
		anchors += DiffAnchor(0, 0)

		for (h in merged) {
			// A hunk straddling a move can't anchor scroll-sync (its two sides sit in different
			// paragraphs), so leave moved hunks out of the monotonic offset map.
			val touchesMove = movePlan.hasMoves &&
				movePlan.overlapsMove(h.leftPlainStart, h.leftPlainEnd, h.rightPlainStart, h.rightPlainEnd)
			if (!touchesMove) {
				anchors += DiffAnchor(
					leftSource = left.plain.plainOffsetToSource(h.leftPlainStart),
					rightSource = right.plain.plainOffsetToSource(h.rightPlainStart),
				)
			}
			// Subtract the moved/stable paragraph ranges so a hunk that merged a move with an
			// adjacent edit still surfaces the edit, without re-highlighting the moved text.
			for (sub in subtractBlocked(h.leftPlainStart, h.leftPlainEnd, movePlan.leftBlocked)) {
				if (left.plain.plain.isBlankRange(sub)) continue
				val range = left.plain.plainRangeToSource(sub.start, sub.endExclusive)
				if (!range.isEmpty) leftSpans += DiffSpan(DiffKind.DELETED, range)
			}
			for (sub in subtractBlocked(h.rightPlainStart, h.rightPlainEnd, movePlan.rightBlocked)) {
				if (right.plain.plain.isBlankRange(sub)) continue
				val range = right.plain.plainRangeToSource(sub.start, sub.endExclusive)
				if (!range.isEmpty) rightSpans += DiffSpan(DiffKind.INSERTED, range)
			}
			if (!touchesMove) {
				anchors += DiffAnchor(
					leftSource = left.plain.plainOffsetToSource(h.leftPlainEnd, preferEnd = true),
					rightSource = right.plain.plainOffsetToSource(h.rightPlainEnd, preferEnd = true),
				)
			}
		}

		for (moved in movePlan.leftMoves) {
			val range = left.plain.plainRangeToSource(moved.range.start, moved.range.endExclusive)
			if (!range.isEmpty) leftSpans += DiffSpan(DiffKind.MOVED, range, moveId = moved.moveId)
		}
		for (moved in movePlan.rightMoves) {
			val range = right.plain.plainRangeToSource(moved.range.start, moved.range.endExclusive)
			if (!range.isEmpty) rightSpans += DiffSpan(DiffKind.MOVED, range, moveId = moved.moveId)
		}

		anchors += DiffAnchor(left.sourceLength, right.sourceLength)

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

private data class Paragraph(
	val range: SourceRange,
	val key: String,
	val index: Int,
)

private data class ParagraphMatch(
	val left: Paragraph,
	val right: Paragraph,
)

private data class MovedParagraph(
	val range: SourceRange,
	val moveId: Int,
)

private data class MovePlan(
	val leftMoves: List<MovedParagraph> = emptyList(),
	val rightMoves: List<MovedParagraph> = emptyList(),
	/** Moved + stable paragraph ranges (sorted by start) to carve out of delete spans. */
	val leftBlocked: List<SourceRange> = emptyList(),
	/** Moved + stable paragraph ranges (sorted by start) to carve out of insert spans. */
	val rightBlocked: List<SourceRange> = emptyList(),
) {
	val hasMoves: Boolean get() = leftMoves.isNotEmpty()

	fun overlapsMove(leftStart: Int, leftEnd: Int, rightStart: Int, rightEnd: Int): Boolean =
		leftMoves.any { rangesOverlap(leftStart, leftEnd, it.range) } ||
			rightMoves.any { rangesOverlap(rightStart, rightEnd, it.range) }
}

private fun detectMovedParagraphs(leftPlain: String, rightPlain: String): MovePlan {
	val leftParagraphs = paragraphs(leftPlain)
	val rightParagraphs = paragraphs(rightPlain)
	if (leftParagraphs.isEmpty() || rightParagraphs.isEmpty()) return MovePlan()

	val leftCounts = leftParagraphs.groupingBy { it.key }.eachCount()
	val rightCounts = rightParagraphs.groupingBy { it.key }.eachCount()
	val rightByKey = rightParagraphs.associateBy { it.key }
	val matches = leftParagraphs.mapNotNull { left ->
		if (leftCounts[left.key] == 1 && rightCounts[left.key] == 1) {
			ParagraphMatch(left, rightByKey.getValue(left.key))
		} else {
			null
		}
	}
	if (matches.isEmpty()) return MovePlan()

	// The stable backbone is the longest run of matches whose relative order is preserved. A
	// backbone of a single paragraph is no backbone at all (one paragraph is trivially "in order"
	// even after teleporting), so a fully reordered set is treated as all-moved.
	val backbone = longestIncreasingParagraphKeys(matches)
	val stableKeys = if (backbone.size >= 2) backbone else emptySet()
	val movedMatches = matches.filter { it.left.key !in stableKeys }
	if (movedMatches.isEmpty()) return MovePlan()

	val leftMoves = ArrayList<MovedParagraph>(movedMatches.size)
	val rightMoves = ArrayList<MovedParagraph>(movedMatches.size)
	for ((moveId, match) in movedMatches.withIndex()) {
		leftMoves += MovedParagraph(match.left.range, moveId)
		rightMoves += MovedParagraph(match.right.range, moveId)
	}
	val stableMatches = matches.filter { it.left.key in stableKeys }

	return MovePlan(
		leftMoves = leftMoves,
		rightMoves = rightMoves,
		leftBlocked = (leftMoves.map { it.range } + stableMatches.map { it.left.range }).sortedBy { it.start },
		rightBlocked = (rightMoves.map { it.range } + stableMatches.map { it.right.range }).sortedBy { it.start },
	)
}

/** Remove the [blockers] (sorted by start, possibly overlapping) from `[start, end)`. */
private fun subtractBlocked(start: Int, end: Int, blockers: List<SourceRange>): List<SourceRange> {
	if (end <= start) return emptyList()
	if (blockers.isEmpty()) return listOf(SourceRange(start, end))
	val out = ArrayList<SourceRange>()
	var cursor = start
	for (b in blockers) {
		if (b.endExclusive <= cursor) continue
		if (b.start >= end) break
		if (b.start > cursor) out += SourceRange(cursor, minOf(b.start, end))
		cursor = maxOf(cursor, b.endExclusive)
		if (cursor >= end) break
	}
	if (cursor < end) out += SourceRange(cursor, end)
	return out
}

private fun String.isBlankRange(range: SourceRange): Boolean {
	for (i in range.start until range.endExclusive) {
		if (!this[i].isWhitespace()) return false
	}
	return true
}

private fun paragraphs(plain: String): List<Paragraph> {
	val paragraphs = ArrayList<Paragraph>()
	var start = 0
	while (start <= plain.length) {
		val newline = plain.indexOf('\n', start)
		val end = if (newline == -1) plain.length else newline
		val key = normalizeParagraph(plain.substring(start, end))
		if (key.isNotEmpty()) {
			paragraphs += Paragraph(SourceRange(start, end), key, paragraphs.size)
		}
		if (newline == -1) break
		start = newline + 1
	}
	return paragraphs
}

private fun longestIncreasingParagraphKeys(matches: List<ParagraphMatch>): Set<String> {
	if (matches.isEmpty()) return emptySet()

	val tails = IntArray(matches.size)
	val previous = IntArray(matches.size) { -1 }
	var size = 0

	for (i in matches.indices) {
		val rightIndex = matches[i].right.index
		var lo = 0
		var hi = size
		while (lo < hi) {
			val mid = (lo + hi) ushr 1
			if (matches[tails[mid]].right.index < rightIndex) {
				lo = mid + 1
			} else {
				hi = mid
			}
		}
		if (lo > 0) previous[i] = tails[lo - 1]
		tails[lo] = i
		if (lo == size) size++
	}

	val stableKeys = HashSet<String>()
	var cursor = tails[size - 1]
	while (cursor != -1) {
		stableKeys += matches[cursor].left.key
		cursor = previous[cursor]
	}
	return stableKeys
}

private val WhitespaceRun = Regex("\\s+")

private fun normalizeParagraph(text: String): String =
	text.replace(WhitespaceRun, " ").trim()

private fun rangesOverlap(start: Int, end: Int, range: SourceRange): Boolean =
	start < range.endExclusive && end > range.start

/**
 * Slide a pure insertion or deletion along its run of equivalent positions so the changed block
 * lands on a paragraph/line boundary when one is reachable. Myers diff may place such a block at
 * any position where the bordering tokens repeat, and its default placement can land mid-paragraph
 * (inserting "Foo.\n\nBar" gets reported as inserting "Bar\n\nFoo"). Preferring a boundary where
 * the block starts and ends on a newline makes inserted paragraphs read as whole paragraphs.
 * Substitutions (both sides non-empty) are left untouched. Returns the adjusted (source, target)
 * positions; both shift by the same amount since the unchanged side's gap moves in lockstep.
 */
private fun slideToBoundary(
	sourcePos: Int,
	sourceLen: Int,
	targetPos: Int,
	targetLen: Int,
	leftTokens: List<Token>,
	rightTokens: List<Token>,
): Pair<Int, Int> {
	val (tokens, blockStart, blockLen) = when {
		sourceLen == 0 && targetLen > 0 -> Triple(rightTokens, targetPos, targetLen)
		targetLen == 0 && sourceLen > 0 -> Triple(leftTokens, sourcePos, sourceLen)
		else -> return sourcePos to targetPos
	}

	var bestStart = blockStart
	var bestScore = boundaryScore(tokens, blockStart, blockLen)

	var p = blockStart
	while (p > 0 && tokens[p - 1].text == tokens[p + blockLen - 1].text) {
		p--
		val s = boundaryScore(tokens, p, blockLen)
		if (s > bestScore) { bestScore = s; bestStart = p }
	}
	p = blockStart
	while (p + blockLen < tokens.size && tokens[p].text == tokens[p + blockLen].text) {
		p++
		val s = boundaryScore(tokens, p, blockLen)
		if (s > bestScore) { bestScore = s; bestStart = p }
	}

	val shift = bestStart - blockStart
	return (sourcePos + shift) to (targetPos + shift)
}

/** +1 if the block starts right after a line break, +1 if it ends on one; higher reads cleaner. */
private fun boundaryScore(tokens: List<Token>, start: Int, len: Int): Int {
	var score = 0
	if (start == 0 || tokens[start - 1].text.endsWith('\n')) score++
	if (start + len == tokens.size || tokens[start + len - 1].text.endsWith('\n')) score++
	return score
}

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
