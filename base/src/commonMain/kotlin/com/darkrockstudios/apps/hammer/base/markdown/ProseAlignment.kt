package com.darkrockstudios.apps.hammer.base.markdown

/** A paragraph's or heading's alignment, as the scene editor writes it. */
enum class ProseAlign(val css: String) {
	LEFT("left"),
	CENTER("center"),
	RIGHT("right"),
	JUSTIFY("justify"),
	;

	/** The class an HTML rendering gives an element with this alignment. */
	val htmlClass: String get() = "align-$css"
}

/** Whether a body paragraph of this alignment keeps its first-line indent: a centred or right-aligned one would sit off its line. */
val ProseAlign?.keepsFirstLineIndent: Boolean get() = this != ProseAlign.CENTER && this != ProseAlign.RIGHT

/**
 * Scene markdown with its alignment wrappers taken out: [markdown] is the text the editor would
 * write without them, and each of its lines that was inside one has that wrapper's alignment.
 */
class AlignedProse(
	val markdown: String,
	private val lineAlignments: Map<Int, ProseAlign>,
) {
	/** Whether any line is aligned. */
	val hasAlignments: Boolean get() = lineAlignments.isNotEmpty()

	/** The alignment of [markdown]'s 0-based [line], or null for the default. */
	fun alignmentOfLine(line: Int): ProseAlign? = lineAlignments[line]

	/** The alignment of the line holding [offset] in [markdown]. */
	fun alignmentAt(offset: Int): ProseAlign? = if (lineAlignments.isEmpty()) null else lineAlignments[lineOf(offset)]

	/** The 0-based line of [markdown] holding [offset]. */
	fun lineOf(offset: Int): Int {
		val found = lineStarts.binarySearch(offset)
		return if (found >= 0) found else -found - 2
	}

	// A List, not an IntArray: IntArray.binarySearch is JVM-only and fails the iOS build.
	private val lineStarts: List<Int> by lazy {
		val starts = ArrayList<Int>().apply { add(0) }
		markdown.forEachIndexed { index, char -> if (char == '\n') starts += index + 1 }
		starts
	}
}

/**
 * Takes out the alignment wrappers the scene editor writes around a run of aligned lines:
 *
 * ```
 * <div align="center">
 *
 * Some *centred* text
 *
 * </div>
 *
 * ```
 *
 * This is the editor's own reading (ComposeTextEditor's `MarkdownAlignment.kt`) under single-newline
 * paragraphs, so the text left is what the editor would write with no alignment at all.
 *
 * A wrapper opens on a `<div>` or `<p>` at the start of a line, with an `align` attribute or a
 * `style` with `text-align`, and closes on the first unescaped closing tag of the same name after
 * it, outside a code span. Text after the opening tag or before the closing one is inside; text
 * after the closing tag is a line of its own after the wrapper. A blank line just inside a tag that
 * stands alone goes, and the one after a closing tag that does. An opening tag with no closing one
 * is text, and so is either tag inside a code fence.
 */
fun stripAlignmentWrappers(markdown: String): AlignedProse {
	if (!mayHaveAlignmentWrapper(markdown)) return AlignedProse(markdown, emptyMap())
	val lines = markdown.split('\n')
	val fenced = fencedLines(lines)
	val wrappers = findWrappers(lines, fenced)
	if (wrappers.isEmpty()) return AlignedProse(markdown, emptyMap())
	fun blank(index: Int) = index !in fenced && lines.getOrNull(index)?.isBlank() == true
	val kept = ArrayList<String>(lines.size)
	val alignments = HashMap<Int, ProseAlign>()
	fun keep(text: String, align: ProseAlign?) {
		align?.let { alignments[kept.size] = it }
		kept += text
	}
	var index = 0
	for ((openLine, opener, close) in wrappers) {
		while (index < openLine) keep(lines[index++], null)
		val line = lines[openLine]
		val closeLine = lines[close.line]
		if (close.line == openLine) {
			line.substring(opener.contentStart, close.start).trim().takeIf { it.isNotEmpty() }?.let { keep(it, opener.align) }
		} else {
			val rest = line.substring(opener.contentStart)
			val lead = closeLine.substring(0, close.start)
			var first = openLine + 1
			var last = close.line - 1
			if (rest.isNotBlank()) keep(rest.trimStart(), opener.align) else if (first <= last && blank(first)) first++
			if (lead.isBlank() && first <= last && blank(last)) last--
			for (inside in first..last) keep(lines[inside], opener.align)
			if (lead.isNotBlank()) keep(lead.trimEnd(), opener.align)
		}
		val trail = closeLine.substring(close.end)
		index = close.line + 1
		if (trail.isNotBlank()) keep(trail.trimStart(), null) else if (index < lines.lastIndex && blank(index)) index++
	}
	while (index < lines.size) keep(lines[index++], null)
	return AlignedProse(kept.joinToString("\n"), alignments)
}

/** The source ranges of the tags [stripAlignmentWrappers] takes out, opening and closing alike. */
fun alignmentTagRanges(markdown: String): List<IntRange> {
	if (!mayHaveAlignmentWrapper(markdown)) return emptyList()
	val lines = markdown.split('\n')
	val wrappers = findWrappers(lines, fencedLines(lines))
	if (wrappers.isEmpty()) return emptyList()
	val lineStarts = IntArray(lines.size)
	for (i in 1 until lines.size) lineStarts[i] = lineStarts[i - 1] + lines[i - 1].length + 1
	return wrappers.flatMap { (openLine, opener, close) ->
		listOf(
			lineStarts[openLine] + opener.tagStart until lineStarts[openLine] + opener.contentStart,
			lineStarts[close.line] + close.start until lineStarts[close.line] + close.end,
		)
	}
}

/**
 * Whether the first [length] characters ([charAt]) hold a line that could open an alignment
 * wrapper: up to three spaces, then `<d` or `<p`. A cheap test before [stripAlignmentWrappers].
 */
inline fun mayHaveAlignmentWrapper(length: Int, charAt: (Int) -> Char): Boolean {
	var lineStart = 0
	while (lineStart < length - 1) {
		var i = lineStart
		while (i < length && i - lineStart < 3 && charAt(i) == ' ') i++
		if (i < length - 1 && charAt(i) == '<') {
			val next = charAt(i + 1).lowercaseChar()
			if (next == 'd' || next == 'p') return true
		}
		while (i < length && charAt(i) != '\n') i++
		lineStart = i + 1
	}
	return false
}

fun mayHaveAlignmentWrapper(text: String): Boolean = mayHaveAlignmentWrapper(text.length, text::get)

private data class Wrapper(val openLine: Int, val opener: Opener, val close: ClosingTag)

/** The wrappers in [lines], in order: each opener with its closing tag, outside [fenced] lines. */
private fun findWrappers(lines: List<String>, fenced: Set<Int>): List<Wrapper> {
	val wrappers = ArrayList<Wrapper>()
	// Per tag, the line whose search for a closing tag found none: no later opener of it can close.
	val unclosedFrom = HashMap<String, Int>()
	var index = 0
	while (index < lines.size) {
		val opener = if (index in fenced) null else readOpener(lines[index])
		val close = opener?.takeIf { index <= (unclosedFrom[it.tag] ?: index) }
			?.let { findClosingTag(lines, fenced, it.tag, index, it.contentStart) }
		if (opener == null || close == null) {
			if (opener != null && opener.tag !in unclosedFrom) unclosedFrom[opener.tag] = index
			index++
			continue
		}
		wrappers += Wrapper(index, opener, close)
		index = close.line + 1
	}
	return wrappers
}

/** The lines inside a ``` or ~~~ fence, markers included, whose text is code. */
private fun fencedLines(lines: List<String>): Set<Int> {
	val fenced = HashSet<Int>()
	var marker: String? = null
	lines.forEachIndexed { index, line ->
		val run = FENCE.find(line)?.groupValues?.get(1)
		val open = marker
		when {
			open != null -> {
				fenced += index
				if (run != null && run[0] == open[0] && run.length >= open.length && line.trim() == run) marker = null
			}

			run != null -> {
				fenced += index
				marker = run
			}
		}
	}
	return fenced
}

private class Opener(val tag: String, val align: ProseAlign, val tagStart: Int, val contentStart: Int)

/** [line] as starting with an opening tag: its tag name, alignment, and where the line's text resumes. */
private fun readOpener(line: String): Opener? {
	val match = OPENING_TAG.find(line) ?: return null
	val attributes = match.groupValues[2]
	val value = ALIGN_ATTRIBUTE.find(attributes)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
		?: TEXT_ALIGN_DECLARATION.find(attributes)?.groupValues?.get(1)
		?: return null
	val align = ProseAlign.entries.firstOrNull { it.css.equals(value, ignoreCase = true) } ?: return null
	return Opener(match.groupValues[1].lowercase(), align, match.range.first, match.range.last + 1)
}

private class ClosingTag(val line: Int, val start: Int, val end: Int)

/** The first closing [tag] from [from] on line [openLine], skipping [fenced] lines after it. */
private fun findClosingTag(lines: List<String>, fenced: Set<Int>, tag: String, openLine: Int, from: Int): ClosingTag? {
	for (index in openLine until lines.size) {
		if (index != openLine && index in fenced) continue
		val line = lines[index]
		for (match in CLOSING_TAG.findAll(line, if (index == openLine) from else 0)) {
			val at = match.range.first
			if (match.groupValues[1].lowercase() != tag || isEscaped(line, at) || inCodeSpan(line, at)) continue
			return ClosingTag(index, at, match.range.last + 1)
		}
	}
	return null
}

/** Whether an odd run of backslashes comes just before [at]. */
private fun isEscaped(line: String, at: Int): Boolean {
	var slashes = 0
	while (at - slashes - 1 >= 0 && line[at - slashes - 1] == '\\') slashes++
	return slashes % 2 == 1
}

/** Whether [at] falls after an unclosed backtick on [line]: inside a code span. */
private fun inCodeSpan(line: String, at: Int): Boolean = (0 until at).count { line[it] == '`' } % 2 == 1

private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
private val OPENING_TAG = Regex("""^ {0,3}<(div|p)(\s[^<>]*)>""", RegexOption.IGNORE_CASE)
private val CLOSING_TAG = Regex("""</(div|p)\s*>""", RegexOption.IGNORE_CASE)
private val ALIGN_ATTRIBUTE = Regex("""(?:^|\s)align\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
private val TEXT_ALIGN_DECLARATION = Regex("""text-align\s*:\s*([A-Za-z]+)""", RegexOption.IGNORE_CASE)
