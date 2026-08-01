package com.darkrockstudios.apps.hammer.common.data.search

private const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/** Leading blockquote, heading, bullet and ordered-list markers, none of which read as prose. */
private val BLOCK_PREFIX = Regex("""^\s*(?:>\s*)*(?:#{1,6}\s+|[-*+]\s+|\d{1,9}[.)]\s+)?""")

/**
 * Compared directly rather than via set or string membership: this runs once per character of every
 * document scanned, and `in` on a CharSequence costs a call per character.
 */
private fun Char.isDelimiter(): Boolean = this == '*' || this == '_' || this == '`'

private fun Char?.isBlankOrEdge(): Boolean = this == null || isWhitespace()

private fun Char?.isPunctuation(): Boolean = this != null && this in ASCII_PUNCTUATION

/**
 * A maximal run of one delimiter character, plus how much of it the projection consumes. Emphasis
 * pairs off from the end of the opener and the start of the closer, so the surviving characters are
 * always the middle of the run.
 */
private class DelimiterRun(val char: Char, val start: Int, val end: Int) {
	var canOpen = false
	var canClose = false

	/** Set for runs sitting inside a resolved code span, where emphasis does not apply. */
	var inert = false
	var dropFront = 0
	var dropBack = 0

	val available: Int get() = end - start - dropFront - dropBack
}

/**
 * Flattens stored Markdown into the prose a reader sees, so a query can match across storage syntax.
 * Backslash escapes resolve to their literal character (`well\-known` becomes `well-known`) and
 * *paired* emphasis or code delimiters are dropped (`**Chapter** One` becomes `Chapter One`).
 *
 * Pairing follows CommonMark's flanking rules, which is what keeps literal markers intact:
 * `user_name`, `2 * 3` and a bare `***` divider all survive because neither side of the delimiter
 * can open or close emphasis. That matters for content the escaping editor did not write, such as
 * imports, synced documents and hand-edited files.
 *
 * Known limits, accepted so a full-project scan stays inside the search debounce: link and image
 * syntax is left intact, so imported content can show a URL in a snippet; punctuation is tested
 * against ASCII only; and escapes resolve inside code spans, because everything the editor saves is
 * escaped throughout. Parsing properly would cover all of these but costs roughly four times as
 * much per scan, which only becomes affordable once projections are cached per document.
 *
 * Block structure is left alone; see [markdownTitleLine] for the leading-marker case.
 */
fun projectMarkdownToPlainText(markdown: String): String {
	if (!containsDelimiter(markdown)) {
		// The editor escapes punctuation on every save, so plain paragraphs still reach this far.
		return if (containsEscape(markdown)) unescape(markdown) else markdown
	}

	val runs = scanDelimiterRuns(markdown)
	resolveCodeSpans(runs)
	resolveEmphasis(runs)
	return render(markdown, runs)
}

/**
 * The prose title for [markdown]: its first non-blank line with block markers stripped and inline
 * markup flattened. A line that projects to nothing falls back to itself, so a marker-only line
 * still yields a title rather than reading as an empty document.
 */
fun markdownTitleLine(markdown: String): String {
	val line = markdown.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
	val body = line.replaceFirst(BLOCK_PREFIX, "")
	val projected = projectMarkdownToPlainText(body).trim()
	return projected.ifBlank { line.trim() }
}

private fun containsDelimiter(text: String): Boolean {
	var i = 0
	val length = text.length
	while (i < length) {
		if (text[i].isDelimiter()) return true
		i++
	}
	return false
}

private fun containsEscape(text: String): Boolean {
	var i = 0
	val length = text.length
	while (i < length) {
		if (text[i] == '\\') return true
		i++
	}
	return false
}

/** Appends the character at [i], resolving a backslash escape, and returns the next index. */
private fun StringBuilder.appendResolved(markdown: String, i: Int): Int {
	val c = markdown[i]
	if (c == '\\' && i + 1 < markdown.length && markdown[i + 1] in ASCII_PUNCTUATION) {
		append(markdown[i + 1])
		return i + 2
	}
	append(c)
	return i + 1
}

/** Resolves escapes alone, for the common document that carries no emphasis at all. */
private fun unescape(markdown: String): String {
	val sb = StringBuilder(markdown.length)
	var i = 0
	while (i < markdown.length) i = sb.appendResolved(markdown, i)
	return sb.toString()
}

private fun scanDelimiterRuns(markdown: String): List<DelimiterRun> {
	val runs = mutableListOf<DelimiterRun>()
	val length = markdown.length
	var i = 0
	while (i < length) {
		val c = markdown[i]
		if (c == '\\' && i + 1 < length && markdown[i + 1] in ASCII_PUNCTUATION) {
			i += 2
			continue
		}
		if (!c.isDelimiter()) {
			i++
			continue
		}

		val start = i
		var end = start + 1
		while (end < length && markdown[end] == c) end++
		i = end

		var canOpen = true
		var canClose = true
		if (c != '`') {
			val prev = markdown.getOrNull(start - 1)
			val next = markdown.getOrNull(end)
			val leftFlanking = !next.isBlankOrEdge() &&
				(!next.isPunctuation() || prev.isBlankOrEdge() || prev.isPunctuation())
			val rightFlanking = !prev.isBlankOrEdge() &&
				(!prev.isPunctuation() || next.isBlankOrEdge() || next.isPunctuation())
			if (c == '*') {
				canOpen = leftFlanking
				canClose = rightFlanking
			} else {
				// Underscores cannot delimit emphasis inside a word, which is what saves `user_name`.
				canOpen = leftFlanking && (!rightFlanking || prev.isPunctuation())
				canClose = rightFlanking && (!leftFlanking || next.isPunctuation())
			}
			// A run that can do neither is literal text; leaving it out keeps it out of the render.
			if (!canOpen && !canClose) continue
		}

		val run = DelimiterRun(c, start, end)
		run.canOpen = canOpen
		run.canClose = canClose
		runs.add(run)
	}
	return runs
}

/** Code spans bind tighter than emphasis, so they claim their delimiters first. */
private fun resolveCodeSpans(runs: List<DelimiterRun>) {
	var i = 0
	while (i < runs.size) {
		val opener = runs[i]
		if (opener.char != '`') {
			i++
			continue
		}

		val width = opener.end - opener.start
		var j = i + 1
		while (j < runs.size) {
			val candidate = runs[j]
			if (candidate.char == '`' && candidate.end - candidate.start == width) break
			j++
		}
		if (j == runs.size) {
			i++
			continue
		}

		opener.dropBack = width
		runs[j].dropFront = width
		for (k in i + 1 until j) runs[k].inert = true
		i = j + 1
	}
}

private fun resolveEmphasis(runs: List<DelimiterRun>) {
	val open = mutableListOf<DelimiterRun>()
	for (run in runs) {
		if (run.char == '`' || run.inert) continue

		if (run.canClose) {
			while (run.available > 0) {
				val openerIndex = open.indexOfLast { it.char == run.char }
				if (openerIndex < 0) break

				val opener = open[openerIndex]
				val consumed = minOf(run.available, opener.available)
				opener.dropBack += consumed
				run.dropFront += consumed

				// Anything stacked above the matched opener can never close now.
				while (open.size > openerIndex) open.removeAt(open.size - 1)
				if (opener.available > 0) open.add(opener)
			}
		}
		if (run.available > 0 && run.canOpen) open.add(run)
	}
}

private fun render(markdown: String, runs: List<DelimiterRun>): String {
	val sb = StringBuilder(markdown.length)
	val length = markdown.length
	var i = 0
	var r = 0
	while (i < length) {
		if (r < runs.size && i == runs[r].start) {
			val run = runs[r]
			sb.append(markdown, run.start + run.dropFront, run.end - run.dropBack)
			i = run.end
			r++
			continue
		}

		i = sb.appendResolved(markdown, i)
	}
	return sb.toString()
}
