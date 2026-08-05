// PR #821 implementation, kept verbatim in the test source set as a benchmark baseline.
package data.search

private const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/**
 * Compared directly rather than via set or string membership: this runs once per character of every
 * document scanned, and `in` on a CharSequence costs a call per character.
 */
private fun Char.isDelimiter(): Boolean = this == '*' || this == '_' || this == '`'

private fun Char.isSpaceOrTab(): Boolean = this == ' ' || this == '\t'

private fun Char?.isBlankOrEdge(): Boolean = this == null || isWhitespace()

private fun Char?.isPunctuation(): Boolean = this != null && this in ASCII_PUNCTUATION

/**
 * A maximal run of one delimiter character, plus how much of it the projection consumes. Emphasis
 * pairs off from the end of the opener and the start of the closer, so the surviving characters are
 * always the middle of the run.
 */
private class DelimiterRun(val char: Char, val start: Int, val end: Int, val paragraph: Int) {
	var canOpen = false
	var canClose = false

	/** Set for runs sitting inside a resolved code span, where emphasis does not apply. */
	var inert = false
	var dropFront = 0
	var dropBack = 0

	val available: Int get() = end - start - dropFront - dropBack
}

/**
 * Flattens stored Markdown into the prose a reader sees. Backslash escapes resolve to their literal
 * character (`well\-known` becomes `well-known`), paired emphasis and code delimiters are dropped
 * (`**Chapter** One` becomes `Chapter One`), and leading blockquote, heading and bullet markers are
 * removed from each line.
 *
 * Delimiters pair only within a paragraph, and only where CommonMark's flanking rules allow, so
 * literal markers survive: `user_name`, `2 * 3`, a bare `***` divider and a stray apostrophe-backtick
 * are all left intact.
 *
 * Link and image syntax is left as written. Punctuation is tested against ASCII only, and escapes
 * resolve inside code spans as well as outside them.
 */
fun legacyProjectMarkdownToPlainText(markdown: String): String {
	if (!legacyContainsMarkdownSyntax(markdown)) return markdown

	val runs = scanDelimiterRuns(markdown)
	resolveCodeSpans(runs)
	resolveEmphasis(runs)
	return render(markdown, runs)
}

/**
 * The prose title for [markdown]: the first non-blank line of its projection. Falls back to the raw
 * first line when the projection of it is blank, so a marker-only line still yields a title rather
 * than reading as an empty document.
 */
fun legacyMarkdownTitleLine(markdown: String): String {
	val projected = legacyProjectMarkdownToPlainText(markdown)
	val title = projected.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
	if (title.isNotEmpty()) return title
	return markdown.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

/**
 * True when [text] holds a character the projection can remove. A query without one that misses the
 * projection cannot match the raw source either, because the projection only ever deletes.
 */
fun legacyContainsMarkdownSyntax(text: String): Boolean {
	var i = 0
	while (i < text.length) {
		val c = text[i]
		if (c.isDelimiter() || c == '\\' || c == '#' || c == '>' || c == '-' || c == '+') return true
		i++
	}
	return false
}

/**
 * The offset after any blockquote, heading or bullet marker opening the line at [start], or [start]
 * itself when the line opens with prose. Ordered-list markers are left alone: a single line cannot
 * distinguish "1. Draft opening" from "1984. The year everything changed".
 */
private fun skipBlockPrefix(markdown: String, start: Int): Int {
	val length = markdown.length
	var i = start
	var found = false

	while (i < length && markdown[i].isSpaceOrTab()) i++

	while (i < length && markdown[i] == '>') {
		i++
		found = true
		while (i < length && markdown[i].isSpaceOrTab()) i++
	}

	if (i < length && markdown[i] == '#') {
		var j = i
		var hashes = 0
		while (j < length && markdown[j] == '#') {
			j++
			hashes++
		}
		if (hashes <= 6 && j < length && markdown[j].isSpaceOrTab()) {
			i = j
			found = true
		}
	} else if (i < length && (markdown[i] == '-' || markdown[i] == '*' || markdown[i] == '+')) {
		if (i + 1 < length && markdown[i + 1].isSpaceOrTab()) {
			i++
			found = true
		}
	}

	if (!found) return start
	while (i < length && markdown[i].isSpaceOrTab()) i++
	return i
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

private fun scanDelimiterRuns(markdown: String): List<DelimiterRun> {
	val runs = mutableListOf<DelimiterRun>()
	val length = markdown.length
	var i = 0
	var paragraph = 0
	while (i < length) {
		val c = markdown[i]
		if (c == '\\' && i + 1 < length && markdown[i + 1] in ASCII_PUNCTUATION) {
			i += 2
			continue
		}
		if (c == '\n') {
			var k = i + 1
			while (k < length && (markdown[k].isSpaceOrTab() || markdown[k] == '\r')) k++
			if (k >= length || markdown[k] == '\n') paragraph++
			i++
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

		val run = DelimiterRun(c, start, end, paragraph)
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
		var closer = -1
		while (j < runs.size && runs[j].paragraph == opener.paragraph) {
			val candidate = runs[j]
			if (candidate.char == '`' && candidate.end - candidate.start == width) {
				closer = j
				break
			}
			j++
		}
		if (closer < 0) {
			i++
			continue
		}

		opener.dropBack = width
		runs[closer].dropFront = width
		for (k in i + 1 until closer) runs[k].inert = true
		i = closer + 1
	}
}

private fun resolveEmphasis(runs: List<DelimiterRun>) {
	val open = mutableListOf<DelimiterRun>()
	var paragraph = -1
	for (run in runs) {
		if (run.paragraph != paragraph) {
			open.clear()
			paragraph = run.paragraph
		}
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
	var atLineStart = true
	while (i < length) {
		if (atLineStart) {
			atLineStart = false
			val afterPrefix = skipBlockPrefix(markdown, i)
			if (afterPrefix > i) {
				i = afterPrefix
				while (r < runs.size && runs[r].start < i) r++
				continue
			}
		}

		if (r < runs.size && i == runs[r].start) {
			val run = runs[r]
			sb.append(markdown, run.start + run.dropFront, run.end - run.dropBack)
			i = run.end
			r++
			continue
		}

		if (markdown[i] == '\n') atLineStart = true
		i = sb.appendResolved(markdown, i)
	}
	return sb.toString()
}
