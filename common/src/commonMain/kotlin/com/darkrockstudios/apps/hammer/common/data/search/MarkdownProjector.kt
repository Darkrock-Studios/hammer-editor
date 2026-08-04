package com.darkrockstudios.apps.hammer.common.data.search

import com.darkrockstudios.apps.hammer.common.util.ScanBuffers

private const val FLAG_CAN_OPEN = 1
private const val FLAG_CAN_CLOSE = 2
private const val FLAG_INERT = 4

private const val INITIAL_OUT = 8 * 1024
private const val INITIAL_RUNS = 256
private const val MIN_RUNS = 16

/**
 * CommonMark's ASCII punctuation set, as the four contiguous ranges it actually is. Tested once per
 * character of every document scanned, so the ranges beat a membership scan over a 32-char string.
 */
internal fun Char.isAsciiPunctuation(): Boolean =
	this in '!'..'/' ||
		this in ':'..'@' ||
		this in '['..'`' ||
		this in '{'..'~'

private fun Char.isDelimiterChar(): Boolean = this == '*' || this == '_' || this == '`'

private fun Char.isSpaceOrTabChar(): Boolean = this == ' ' || this == '\t'

/**
 * A reusable workspace for [projectMarkdownToPlainText]'s scan. Every buffer grows to the largest
 * document it has seen and is then reused, so scanning a project a second time allocates nothing:
 * the delimiter runs live in parallel primitive arrays rather than objects, and the projected prose
 * lands in a char buffer that is matched against in place rather than being turned into a String.
 *
 * Not thread safe. A concurrent scan needs its own instance.
 */
class MarkdownProjector(initialCapacity: Int = INITIAL_OUT) : ScanBuffers {

	/**
	 * The source is copied in and then indexed as a flat array: the scan and the render each walk
	 * every character, and going through `CharSequence` would pay an interface call on both passes.
	 */
	private var src = CharArray(initialCapacity)
	private var srcLength = 0

	private var rawBytes = ByteArray(0)

	/** The projection only ever deletes, so it can never outgrow the source it came from. */
	private var out = CharArray(initialCapacity)
	private var outLength = 0

	/**
	 * Sized from the document rather than a fixed floor, so projecting one short note does not pay
	 * for a novel's worth of run slots. Grows to whatever the widest document actually needs.
	 */
	private val initialRuns = (initialCapacity / 32).coerceIn(MIN_RUNS, INITIAL_RUNS)

	private var runChar = CharArray(initialRuns)
	private var runStart = IntArray(initialRuns)
	private var runEnd = IntArray(initialRuns)
	private var runParagraph = IntArray(initialRuns)
	private var runDropFront = IntArray(initialRuns)
	private var runDropBack = IntArray(initialRuns)
	private var runFlags = IntArray(initialRuns)
	private var runCount = 0

	private var openStack = IntArray(initialRuns)
	private var openCount = 0

	/** Length of the projected prose currently held in the buffer. */
	val length: Int get() = outLength

	operator fun get(index: Int): Char = out[index]

	/**
	 * Projects [source] into this workspace and returns the projected length. The result stays valid
	 * until the next call.
	 */
	fun project(source: CharSequence): Int {
		val length = source.length
		ensureSource(length)
		var i = 0
		while (i < length) {
			src[i] = source[i]
			i++
		}
		return projectSource(length)
	}

	/**
	 * The source buffer, grown to hold at least [minCapacity] characters. Fill it directly and then
	 * call [projectSource] to skip copying the document in at all.
	 */
	fun sourceBuffer(minCapacity: Int): CharArray {
		ensureSource(minCapacity)
		return src
	}

	override fun charBuffer(minCapacity: Int): CharArray = sourceBuffer(minCapacity)

	/** Scratch for the raw bytes of a document being read in, before they are decoded into [src]. */
	override fun byteBuffer(minCapacity: Int): ByteArray {
		if (rawBytes.size < minCapacity) rawBytes = ByteArray(minCapacity)
		return rawBytes
	}

	/** Projects the first [length] characters already sitting in [sourceBuffer]. */
	fun projectSource(length: Int): Int {
		srcLength = length
		outLength = 0
		ensureOut(length)
		scanDelimiterRuns()
		resolveCodeSpans()
		resolveEmphasis()
		render()
		return outLength
	}

	/**
	 * Index of [query] in the projected prose, or -1. Compares against the buffer directly so a scan
	 * that finds nothing never builds a string.
	 */
	fun indexOf(query: String, ignoreCase: Boolean = true): Int {
		val queryLength = query.length
		if (queryLength == 0 || queryLength > outLength) return -1
		val buffer = out
		val first = query[0]
		val last = outLength - queryLength
		var i = 0
		outer@ while (i <= last) {
			if (!buffer[i].equals(first, ignoreCase)) {
				i++
				continue
			}
			var j = 1
			while (j < queryLength) {
				if (!buffer[i + j].equals(query[j], ignoreCase)) {
					i++
					continue@outer
				}
				j++
			}
			return i
		}
		return -1
	}

	/** The projected prose as a String. Allocates, so reserve it for a hit. */
	fun substring(startIndex: Int, endIndex: Int): String {
		val from = startIndex.coerceIn(0, outLength)
		val to = endIndex.coerceIn(from, outLength)
		return out.concatToString(from, to)
	}

	fun projected(): String = substring(0, outLength)

	/**
	 * Up to [maxChars] of the projection with runs of whitespace collapsed to a single space, and an
	 * ellipsis when prose remained. Walks the buffer, so previewing never materializes the whole
	 * document just to throw most of it away.
	 */
	fun collapsedPreview(maxChars: Int): String {
		val sb = StringBuilder(maxChars + 1)
		var i = 0
		var pendingSpace = false
		while (i < outLength && sb.length <= maxChars) {
			val c = out[i]
			if (c.isWhitespace()) {
				if (sb.isNotEmpty()) pendingSpace = true
			} else {
				if (pendingSpace) {
					sb.append(' ')
					pendingSpace = false
				}
				sb.append(c)
			}
			i++
		}
		// Anything left that is not whitespace means the preview is a truncation, not the whole text.
		var more = false
		while (i < outLength) {
			if (!out[i].isWhitespace()) {
				more = true
				break
			}
			i++
		}
		if (sb.length > maxChars || more) {
			while (sb.length > maxChars) sb.deleteAt(sb.length - 1)
			while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.deleteAt(sb.length - 1)
			sb.append('…')
		}
		return sb.toString()
	}

	/** The first non-blank line of the projection, or "" when every line is blank. */
	fun firstNonBlankLine(): String {
		var i = 0
		while (i < outLength) {
			var end = i
			while (end < outLength && out[end] != '\n') end++
			var start = i
			while (start < end && out[start].isWhitespace()) start++
			var stop = end
			while (stop > start && out[stop - 1].isWhitespace()) stop--
			if (stop > start) return out.concatToString(start, stop)
			i = end + 1
		}
		return ""
	}

	private fun ensureSource(needed: Int) {
		if (src.size < needed) src = CharArray(needed)
	}

	private fun ensureOut(needed: Int) {
		if (out.size < needed) out = CharArray(needed)
	}

	private fun ensureRuns(needed: Int) {
		if (runChar.size >= needed) return
		val size = needed * 2
		runChar = runChar.copyOf(size)
		runStart = runStart.copyOf(size)
		runEnd = runEnd.copyOf(size)
		runParagraph = runParagraph.copyOf(size)
		runDropFront = runDropFront.copyOf(size)
		runDropBack = runDropBack.copyOf(size)
		runFlags = runFlags.copyOf(size)
		openStack = openStack.copyOf(size)
	}

	private fun available(run: Int): Int =
		runEnd[run] - runStart[run] - runDropFront[run] - runDropBack[run]

	private fun addRun(char: Char, start: Int, end: Int, paragraph: Int, flags: Int) {
		ensureRuns(runCount + 1)
		runChar[runCount] = char
		runStart[runCount] = start
		runEnd[runCount] = end
		runParagraph[runCount] = paragraph
		runDropFront[runCount] = 0
		runDropBack[runCount] = 0
		runFlags[runCount] = flags
		runCount++
	}

	private fun isBlankOrEdge(index: Int): Boolean =
		index < 0 || index >= srcLength || src[index].isWhitespace()

	private fun isPunctuationAt(index: Int): Boolean =
		index >= 0 && index < srcLength && src[index].isAsciiPunctuation()

	private fun scanDelimiterRuns() {
		runCount = 0
		val length = srcLength
		var i = 0
		var paragraph = 0
		while (i < length) {
			val c = src[i]
			if (c == '\\' && i + 1 < length && src[i + 1].isAsciiPunctuation()) {
				i += 2
				continue
			}
			if (c == '\n') {
				var k = i + 1
				while (k < length && (src[k].isSpaceOrTabChar() || src[k] == '\r')) k++
				if (k >= length || src[k] == '\n') paragraph++
				i++
				continue
			}
			if (!c.isDelimiterChar()) {
				i++
				continue
			}

			val start = i
			var end = start + 1
			while (end < length && src[end] == c) end++
			i = end

			var canOpen = true
			var canClose = true
			if (c != '`') {
				val prevBlank = isBlankOrEdge(start - 1)
				val nextBlank = isBlankOrEdge(end)
				val prevPunct = isPunctuationAt(start - 1)
				val nextPunct = isPunctuationAt(end)
				val leftFlanking = !nextBlank && (!nextPunct || prevBlank || prevPunct)
				val rightFlanking = !prevBlank && (!prevPunct || nextBlank || nextPunct)
				if (c == '*') {
					canOpen = leftFlanking
					canClose = rightFlanking
				} else {
					// Underscores cannot delimit emphasis inside a word, which is what saves `user_name`.
					canOpen = leftFlanking && (!rightFlanking || prevPunct)
					canClose = rightFlanking && (!leftFlanking || nextPunct)
				}
				// A run that can do neither is literal text; leaving it out keeps it out of the render.
				if (!canOpen && !canClose) continue
			}

			var flags = 0
			if (canOpen) flags = flags or FLAG_CAN_OPEN
			if (canClose) flags = flags or FLAG_CAN_CLOSE
			addRun(c, start, end, paragraph, flags)
		}
	}

	/** Code spans bind tighter than emphasis, so they claim their delimiters first. */
	private fun resolveCodeSpans() {
		var i = 0
		while (i < runCount) {
			if (runChar[i] != '`') {
				i++
				continue
			}

			val width = runEnd[i] - runStart[i]
			var j = i + 1
			var closer = -1
			while (j < runCount && runParagraph[j] == runParagraph[i]) {
				if (runChar[j] == '`' && runEnd[j] - runStart[j] == width) {
					closer = j
					break
				}
				j++
			}
			if (closer < 0) {
				i++
				continue
			}

			runDropBack[i] = width
			runDropFront[closer] = width
			for (k in i + 1 until closer) runFlags[k] = runFlags[k] or FLAG_INERT
			i = closer + 1
		}
	}

	private fun resolveEmphasis() {
		openCount = 0
		var paragraph = -1
		for (run in 0 until runCount) {
			if (runParagraph[run] != paragraph) {
				openCount = 0
				paragraph = runParagraph[run]
			}
			if (runChar[run] == '`' || runFlags[run] and FLAG_INERT != 0) continue

			if (runFlags[run] and FLAG_CAN_CLOSE != 0) {
				while (available(run) > 0) {
					var openerSlot = -1
					var s = openCount - 1
					while (s >= 0) {
						if (runChar[openStack[s]] == runChar[run]) {
							openerSlot = s
							break
						}
						s--
					}
					if (openerSlot < 0) break

					val opener = openStack[openerSlot]
					val consumed = minOf(available(run), available(opener))
					runDropBack[opener] += consumed
					runDropFront[run] += consumed

					// Anything stacked above the matched opener can never close now.
					openCount = openerSlot
					if (available(opener) > 0) pushOpen(opener)
				}
			}
			if (available(run) > 0 && runFlags[run] and FLAG_CAN_OPEN != 0) pushOpen(run)
		}
	}

	private fun pushOpen(run: Int) {
		ensureRuns(openCount + 1)
		openStack[openCount] = run
		openCount++
	}

	/**
	 * The offset after any blockquote, heading or bullet marker opening the line at [start], or
	 * [start] itself when the line opens with prose. Ordered-list markers are left alone: a single
	 * line cannot distinguish "1. Draft opening" from "1984. The year everything changed".
	 */
	private fun skipBlockPrefix(start: Int): Int {
		val length = srcLength
		var i = start
		var found = false

		while (i < length && src[i].isSpaceOrTabChar()) i++

		while (i < length && src[i] == '>') {
			i++
			found = true
			while (i < length && src[i].isSpaceOrTabChar()) i++
		}

		if (i < length && src[i] == '#') {
			var j = i
			var hashes = 0
			while (j < length && src[j] == '#') {
				j++
				hashes++
			}
			if (hashes <= 6 && j < length && src[j].isSpaceOrTabChar()) {
				i = j
				found = true
			}
		} else if (i < length && (src[i] == '-' || src[i] == '*' || src[i] == '+')) {
			if (i + 1 < length && src[i + 1].isSpaceOrTabChar()) {
				i++
				found = true
			}
		}

		if (!found) return start
		while (i < length && src[i].isSpaceOrTabChar()) i++
		return i
	}

	private fun appendOut(c: Char) {
		out[outLength] = c
		outLength++
	}

	private fun appendOut(from: Int, to: Int) {
		var i = from
		while (i < to) {
			out[outLength] = src[i]
			outLength++
			i++
		}
	}

	private fun render() {
		val length = srcLength
		var i = 0
		var r = 0
		var atLineStart = true
		while (i < length) {
			if (atLineStart) {
				atLineStart = false
				val afterPrefix = skipBlockPrefix(i)
				if (afterPrefix > i) {
					i = afterPrefix
					while (r < runCount && runStart[r] < i) r++
					continue
				}
			}

			if (r < runCount && i == runStart[r]) {
				appendOut(runStart[r] + runDropFront[r], runEnd[r] - runDropBack[r])
				i = runEnd[r]
				r++
				continue
			}

			val c = src[i]
			if (c == '\n') atLineStart = true
			if (c == '\\' && i + 1 < length && src[i + 1].isAsciiPunctuation()) {
				appendOut(src[i + 1])
				i += 2
			} else {
				appendOut(c)
				i++
			}
		}
	}
}
