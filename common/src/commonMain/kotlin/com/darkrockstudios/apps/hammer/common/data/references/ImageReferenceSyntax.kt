package com.darkrockstudios.apps.hammer.common.data.references

/**
 * How Markdown refers to an Image entity: `![name](hammer://image/<id>)`. Every function returns
 * the identical String instance when it changes nothing, so callers can skip the write.
 */
object ImageReferenceSyntax {
	const val SCHEME_PREFIX = "hammer://image/"

	fun reference(imageId: Int): String = "$SCHEME_PREFIX$imageId"

	/** The image id an image source such as `hammer://image/3` names, or null when it names none. */
	fun parseId(source: String): Int? {
		if (!source.startsWith(SCHEME_PREFIX)) return null
		val digits = source.substring(SCHEME_PREFIX.length)
		if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
		return digits.toIntOrNull()
	}

	// The id runs to the last digit and must not continue into a word ("hammer://image/12x" is not
	// image 12), and the scheme must not be the tail of a longer one.
	private val ID_PATTERN = Regex("""(?<![\w+.-])hammer://image/(\d+)(?!\w)""")

	// Link text with backslash escapes and one level of nested brackets, as Markdown writers emit.
	private const val ALT = """(?:\\.|[^\[\]\\\r\n]|\[(?:\\.|[^\[\]\\\r\n])*\])*"""
	private const val TITLE = """(?:"(?:\\.|[^"\\\r\n])*"|'(?:\\.|[^'\\\r\n])*'|\((?:\\.|[^()\\\r\n])*\))"""
	private const val LABEL = """(?:\\.|[^\[\]\\\r\n])+"""

	// Blockquote markers and indentation in front of a block.
	private const val LINE_PREFIX = """[ \t]*(?:>[ \t]*)*"""

	private val FENCE = Regex("""^[ \t]{0,3}(`{3,}|~{3,})(.*)$""")
	private val BLANK = Regex("""^[ \t]*(?:>[ \t]*)*$""")
	private val LINE = Regex("""[^\r\n]*(?:\r\n|\n|\r)?""")
	private val REFERENCE_USE = Regex("""!\[($ALT)\](?:\[($LABEL)?\])?(?![\[(:])""")
	private val WHITESPACE = Regex("""\s+""")

	/** Every image id the text refers to, including inside code. */
	fun findIds(text: String): Set<Int> {
		if (!text.contains(SCHEME_PREFIX)) return emptySet()
		return ID_PATTERN.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
	}

	/** Rewrites every reference to a key of [idMap] in one pass, so `{1 -> 2, 2 -> 3}` never chains. */
	fun remap(text: String, idMap: Map<Int, Int>): String {
		if (idMap.isEmpty() || !text.contains(SCHEME_PREFIX)) return text

		var changed = false
		val result = ID_PATTERN.replace(text) { match ->
			val newId = match.groupValues[1].toIntOrNull()?.let(idMap::get)
			if (newId == null) {
				match.value
			} else {
				changed = true
				reference(newId)
			}
		}
		return if (changed && result != text) result else text
	}

	/**
	 * Removes the images that show [imageId]. A line holding only such an image (or a reference
	 * definition pointing at it) is dropped, and a blank line left doubled by that is dropped too.
	 * An image sharing its line with other content is cut out of the line. Fenced code is left alone.
	 */
	fun remove(text: String, imageId: Int): String {
		if (imageId !in findIds(text)) return text

		val destination = destinationPattern("$imageId")
		val inline = inlinePattern(destination)
		val wholeLine = Regex("""^$LINE_PREFIX${inline.pattern}[ \t]*$""")
		val definition = definitionPattern(destination)

		val lines = splitLines(text)
		val code = fencedLines(lines)

		val removedLabels = lines.indices
			.filterNot { code[it] }
			.mapNotNull { definition.matchEntire(lines[it].content)?.groupValues?.get(1)?.let(::normalizeLabel) }
			.toSet()

		val dropped = BooleanArray(lines.size)
		var changed = false
		for (i in lines.indices) {
			if (code[i]) continue
			val content = lines[i].content
			if (wholeLine.matches(content) || definition.matches(content)) {
				dropped[i] = true
				changed = true
				continue
			}

			val stripped = replaceReferenceUses(inline.replace(content, ""), removedLabels) { "" }
			if (stripped != content) {
				changed = true
				if (BLANK.matches(stripped) && !BLANK.matches(content)) {
					dropped[i] = true
				} else {
					lines[i] = lines[i].copy(content = stripped)
				}
			}
		}
		if (!changed) return text

		collapseBlankLines(lines, dropped)
		return buildString {
			lines.forEachIndexed { i, line -> if (!dropped[i]) append(line.content).append(line.terminator) }
		}
	}

	/**
	 * Replaces every image that shows a project image with what [replacement] makes of its alt
	 * text, whether the image stands on a line of its own or sits inside prose, and whether it is
	 * inline (`![alt](hammer://image/3)`) or uses a reference (`![alt][label]`). A reference
	 * definition pointing at a project image is dropped, as [remove] drops one. Fenced code is left
	 * alone.
	 */
	fun replaceImages(text: String, replacement: (alt: String) -> String): String {
		if (findIds(text).isEmpty()) return text

		val destination = destinationPattern("""\d+""")
		val inline = inlinePattern(destination)
		val definition = definitionPattern(destination)

		val lines = splitLines(text)
		val code = fencedLines(lines)

		val replacedLabels = lines.indices
			.filterNot { code[it] }
			.mapNotNull { definition.matchEntire(lines[it].content)?.groupValues?.get(1)?.let(::normalizeLabel) }
			.toSet()

		val dropped = BooleanArray(lines.size)
		var changed = false
		for (i in lines.indices) {
			if (code[i]) continue
			val content = lines[i].content
			if (definition.matches(content)) {
				dropped[i] = true
				changed = true
				continue
			}

			val inlineReplaced = inline.replace(content) { replacement(it.groupValues[1]) }
			val replaced = replaceReferenceUses(inlineReplaced, replacedLabels, replacement)
			if (replaced != content) {
				changed = true
				lines[i] = lines[i].copy(content = replaced)
			}
		}
		if (!changed) return text

		collapseBlankLines(lines, dropped)
		return buildString {
			lines.forEachIndexed { i, line -> if (!dropped[i]) append(line.content).append(line.terminator) }
		}
	}

	private fun destinationPattern(id: String): String = """(?:<hammer://image/$id>|hammer://image/$id)"""

	private fun inlinePattern(destination: String): Regex =
		Regex("""!\[($ALT)\]\([ \t]*$destination(?:[ \t]+$TITLE)?[ \t]*\)""")

	private fun definitionPattern(destination: String): Regex =
		Regex("""^$LINE_PREFIX\[($LABEL)\]:[ \t]*$destination(?:[ \t]+$TITLE)?[ \t]*$""")

	private fun replaceReferenceUses(
		content: String,
		labels: Set<String>,
		replacement: (alt: String) -> String,
	): String {
		if (labels.isEmpty()) return content
		return REFERENCE_USE.replace(content) { match ->
			val label = match.groups[2]?.value ?: match.groupValues[1]
			if (normalizeLabel(label) in labels) replacement(match.groupValues[1]) else match.value
		}
	}

	// Markdown matches reference labels case-insensitively with runs of whitespace collapsed.
	private fun normalizeLabel(label: String): String = label.trim().replace(WHITESPACE, " ").lowercase()

	/**
	 * Each run of dropped lines also takes one neighbouring blank line when it sat between two, or
	 * between a blank line and either end of the text.
	 */
	private fun collapseBlankLines(lines: List<Line>, dropped: BooleanArray) {
		var i = 0
		while (i < lines.size) {
			if (!dropped[i]) {
				i++
				continue
			}
			var end = i
			while (end + 1 < lines.size && dropped[end + 1]) end++

			val before = (i - 1 downTo 0).firstOrNull { !dropped[it] }
			val after = (end + 1).takeIf { it < lines.size }
			val blankBefore = before == null || BLANK.matches(lines[before].content)
			val blankAfter = after != null && BLANK.matches(lines[after].content)
			if (blankBefore && blankAfter) {
				dropped[after] = true
				// The blank line just dropped is not a new run to collapse around.
				end = after
			} else if (after == null && before != null && blankBefore) {
				dropped[before] = true
			}
			i = end + 1
		}
	}

	private fun fencedLines(lines: List<Line>): BooleanArray {
		val code = BooleanArray(lines.size)
		var fence: String? = null
		for (i in lines.indices) {
			val match = FENCE.matchEntire(lines[i].content)
			val open = fence
			if (open == null) {
				if (match != null) {
					fence = match.groupValues[1]
					code[i] = true
				}
			} else {
				code[i] = true
				if (match != null && isClosingFence(match, open)) fence = null
			}
		}
		return code
	}

	private fun isClosingFence(match: MatchResult, open: String): Boolean {
		val marker = match.groupValues[1]
		return marker[0] == open[0] && marker.length >= open.length && match.groupValues[2].isBlank()
	}

	private data class Line(val content: String, val terminator: String)

	private fun splitLines(text: String): MutableList<Line> =
		LINE.findAll(text)
			.map { it.value }
			.filter { it.isNotEmpty() }
			.map { raw ->
				val content = raw.trimEnd('\r', '\n')
				Line(content, raw.substring(content.length))
			}
			.toMutableList()
}
