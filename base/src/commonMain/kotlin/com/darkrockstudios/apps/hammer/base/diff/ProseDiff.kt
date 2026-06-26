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
		val movePlan = detectMovedParagraphs(merged, left.plain.plain, right.plain.plain)

		val leftSpans = ArrayList<DiffSpan>(merged.size)
		val rightSpans = ArrayList<DiffSpan>(merged.size)
		val anchors = ArrayList<DiffAnchor>(merged.size * 2 + 2)
		val emittedLeftMoves = HashSet<Int>()
		val emittedRightMoves = HashSet<Int>()
		anchors += DiffAnchor(0, 0)

		for ((index, h) in merged.withIndex()) {
			val hunkMoveId = movePlan.hunkMoveIds[index]
			val leftMoved = movePlan.leftMovedParagraph(h.leftPlainStart, h.leftPlainEnd)
			val rightMoved = movePlan.rightMovedParagraph(h.rightPlainStart, h.rightPlainEnd)
			val leftStable = movePlan.isStableLeftParagraph(h.leftPlainStart, h.leftPlainEnd)
			val rightStable = movePlan.isStableRightParagraph(h.rightPlainStart, h.rightPlainEnd)
			val skipAnchors = hunkMoveId != null ||
				(movePlan.hasParagraphMoves && (leftMoved != null || rightMoved != null || leftStable || rightStable))
			if (!skipAnchors) {
				anchors += DiffAnchor(
					leftSource = left.plain.plainOffsetToSource(h.leftPlainStart),
					rightSource = right.plain.plainOffsetToSource(h.rightPlainStart),
				)
			}
			if (h.leftPlainEnd > h.leftPlainStart) {
				when {
					hunkMoveId != null -> {
						val range = left.plain.plainRangeToSource(h.leftPlainStart, h.leftPlainEnd)
						if (!range.isEmpty) {
							leftSpans += DiffSpan(DiffKind.MOVED, range, moveId = hunkMoveId)
						}
					}

					leftMoved != null -> {
						if (emittedLeftMoves.add(leftMoved.moveId)) {
							val range = left.plain.plainRangeToSource(leftMoved.range.start, leftMoved.range.endExclusive)
							if (!range.isEmpty) {
								leftSpans += DiffSpan(DiffKind.MOVED, range, moveId = leftMoved.moveId)
							}
						}
					}

					!leftStable -> {
						val range = left.plain.plainRangeToSource(h.leftPlainStart, h.leftPlainEnd)
						if (!range.isEmpty) leftSpans += DiffSpan(DiffKind.DELETED, range)
					}
				}
			}
			if (h.rightPlainEnd > h.rightPlainStart) {
				when {
					hunkMoveId != null -> {
						val range = right.plain.plainRangeToSource(h.rightPlainStart, h.rightPlainEnd)
						if (!range.isEmpty) {
							rightSpans += DiffSpan(DiffKind.MOVED, range, moveId = hunkMoveId)
						}
					}

					rightMoved != null -> {
						if (emittedRightMoves.add(rightMoved.moveId)) {
							val range = right.plain.plainRangeToSource(rightMoved.range.start, rightMoved.range.endExclusive)
							if (!range.isEmpty) {
								rightSpans += DiffSpan(DiffKind.MOVED, range, moveId = rightMoved.moveId)
							}
						}
					}

					!rightStable -> {
						val range = right.plain.plainRangeToSource(h.rightPlainStart, h.rightPlainEnd)
						if (!range.isEmpty) rightSpans += DiffSpan(DiffKind.INSERTED, range)
					}
				}
			}
			if (!skipAnchors) {
				anchors += DiffAnchor(
					leftSource = left.plain.plainOffsetToSource(h.leftPlainEnd, preferEnd = true),
					rightSource = right.plain.plainOffsetToSource(h.rightPlainEnd, preferEnd = true),
				)
			}
		}

		for (moved in movePlan.leftMovedParagraphs) {
			if (emittedLeftMoves.add(moved.moveId)) {
				val range = left.plain.plainRangeToSource(moved.range.start, moved.range.endExclusive)
				if (!range.isEmpty) leftSpans += DiffSpan(DiffKind.MOVED, range, moveId = moved.moveId)
			}
		}
		for (moved in movePlan.rightMovedParagraphs) {
			if (emittedRightMoves.add(moved.moveId)) {
				val range = right.plain.plainRangeToSource(moved.range.start, moved.range.endExclusive)
				if (!range.isEmpty) rightSpans += DiffSpan(DiffKind.MOVED, range, moveId = moved.moveId)
			}
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

private data class MoveCandidate(
	val hunkIndex: Int,
	val key: String,
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
	val hunkMoveIds: Map<Int, Int> = emptyMap(),
	val leftMovedParagraphs: List<MovedParagraph> = emptyList(),
	val rightMovedParagraphs: List<MovedParagraph> = emptyList(),
	val leftStableParagraphs: List<SourceRange> = emptyList(),
	val rightStableParagraphs: List<SourceRange> = emptyList(),
) {
	val hasParagraphMoves: Boolean get() = leftMovedParagraphs.isNotEmpty() || rightMovedParagraphs.isNotEmpty()

	fun leftMovedParagraph(start: Int, end: Int): MovedParagraph? =
		leftMovedParagraphs.firstOrNull { rangesOverlap(start, end, it.range) }

	fun rightMovedParagraph(start: Int, end: Int): MovedParagraph? =
		rightMovedParagraphs.firstOrNull { rangesOverlap(start, end, it.range) }

	fun isStableLeftParagraph(start: Int, end: Int): Boolean =
		hasParagraphMoves && leftStableParagraphs.any { rangesOverlap(start, end, it) }

	fun isStableRightParagraph(start: Int, end: Int): Boolean =
		hasParagraphMoves && rightStableParagraphs.any { rangesOverlap(start, end, it) }
}

private fun detectMovedParagraphs(
	hunks: List<Hunk>,
	leftPlain: String,
	rightPlain: String,
): MovePlan {
	val hunkMoveIds = detectPureMovedParagraphHunks(hunks, leftPlain, rightPlain)
	if (hunkMoveIds.isNotEmpty()) return MovePlan(hunkMoveIds = hunkMoveIds)

	return detectMovedParagraphsBySequence(leftPlain, rightPlain)
}

private fun detectPureMovedParagraphHunks(
	hunks: List<Hunk>,
	leftPlain: String,
	rightPlain: String,
): Map<Int, Int> {
	val deletes = ArrayList<MoveCandidate>()
	val inserts = ArrayList<MoveCandidate>()

	for ((index, h) in hunks.withIndex()) {
		if (isPureDeleteParagraphHunk(h, leftPlain)) {
			val key = normalizeParagraph(leftPlain.substring(h.leftPlainStart, h.leftPlainEnd))
			if (key.isNotEmpty()) deletes += MoveCandidate(index, key)
		}
		if (isPureInsertParagraphHunk(h, rightPlain)) {
			val key = normalizeParagraph(rightPlain.substring(h.rightPlainStart, h.rightPlainEnd))
			if (key.isNotEmpty()) inserts += MoveCandidate(index, key)
		}
	}

	val deleteCounts = deletes.groupingBy { it.key }.eachCount()
	val insertCounts = inserts.groupingBy { it.key }.eachCount()
	val insertsByKey = inserts.associateBy { it.key }
	val movedHunks = LinkedHashMap<Int, Int>()
	var nextMoveId = 0

	for (delete in deletes) {
		if (deleteCounts[delete.key] == 1 && insertCounts[delete.key] == 1) {
			val insert = insertsByKey.getValue(delete.key)
			movedHunks[delete.hunkIndex] = nextMoveId
			movedHunks[insert.hunkIndex] = nextMoveId
			nextMoveId++
		}
	}

	return movedHunks
}

private fun detectMovedParagraphsBySequence(leftPlain: String, rightPlain: String): MovePlan {
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

	val stableKeys = longestIncreasingParagraphKeys(matches)
	val movedMatches = matches.filter { it.left.key !in stableKeys }
	if (movedMatches.isEmpty()) return MovePlan()

	val leftMoved = ArrayList<MovedParagraph>(movedMatches.size)
	val rightMoved = ArrayList<MovedParagraph>(movedMatches.size)
	for ((moveId, match) in movedMatches.withIndex()) {
		leftMoved += MovedParagraph(match.left.range, moveId)
		rightMoved += MovedParagraph(match.right.range, moveId)
	}

	return MovePlan(
		leftMovedParagraphs = leftMoved,
		rightMovedParagraphs = rightMoved,
		leftStableParagraphs = matches.filter { it.left.key in stableKeys }.map { it.left.range },
		rightStableParagraphs = matches.filter { it.left.key in stableKeys }.map { it.right.range },
	)
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

private fun isPureDeleteParagraphHunk(h: Hunk, leftPlain: String): Boolean =
	h.leftPlainEnd > h.leftPlainStart &&
		h.rightPlainEnd == h.rightPlainStart &&
		isCompleteParagraph(leftPlain, h.leftPlainStart, h.leftPlainEnd)

private fun isPureInsertParagraphHunk(h: Hunk, rightPlain: String): Boolean =
	h.rightPlainEnd > h.rightPlainStart &&
		h.leftPlainEnd == h.leftPlainStart &&
		isCompleteParagraph(rightPlain, h.rightPlainStart, h.rightPlainEnd)

private fun isCompleteParagraph(plain: String, start: Int, end: Int): Boolean {
	if (start < 0 || end < start || end > plain.length) return false
	val startsAtBoundary = start == 0 || plain[start - 1] == '\n'
	val endsAtBoundary = end == plain.length || plain[end] == '\n'
	return startsAtBoundary && endsAtBoundary
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
