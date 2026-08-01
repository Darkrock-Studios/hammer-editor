package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy

/**
 * Imports a Markdown document. Chapters come from ATX (`#`) and Setext (`===`/`---`) headings, and
 * in [MarkdownSplitStrategy.Auto] from bold-only lines when the document has next to no heading
 * markup. [MarkdownSplitStrategy.Pattern] ignores markup entirely and splits on a user regex.
 */
class MarkdownStoryImporter : StoryImporter {
	override val format: ImportFormat = ImportFormat.Markdown

	override fun preview(
		sourceName: String,
		content: ByteArray,
		options: ImportOptions,
	): ImportPreview {
		val text = content.decodeToString().removePrefix(BOM)
		val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")

		val strategy = options.markdownSplitStrategy
		val tokens = if (strategy == MarkdownSplitStrategy.Pattern) {
			tokenizeByPattern(lines, options.markdownChapterPattern)
		} else {
			tokenizeByMarkup(lines, detectBoldLines = strategy == MarkdownSplitStrategy.Auto)
		}

		val fallbackName = sanitizeImportName(sourceName)
		return when (strategy) {
			MarkdownSplitStrategy.H1,
			MarkdownSplitStrategy.Pattern,
				-> buildImportPreview(tokens, 1, options.createChapterGroups, fallbackName)

			MarkdownSplitStrategy.H2 -> buildImportPreview(tokens, 2, options.createChapterGroups, fallbackName)
			MarkdownSplitStrategy.Auto -> previewAuto(tokens, options.createChapterGroups, fallbackName)
		}
	}

	/**
	 * Detects the chapter level instead of asking for it: a lone heading at the shallowest level,
	 * sitting above everything, is taken as the story title (surfaced via [ImportPreview.title] and
	 * dropped from the scenes). The chapter/scene level is then the most frequent remaining level, so
	 * any shallower headings (e.g. parts) fold into groups.
	 */
	private fun previewAuto(
		tokens: List<ImportToken>,
		wrapTopLevelInGroups: Boolean,
		fallbackName: String,
	): ImportPreview {
		val headings = tokens.withIndex().filter { it.value is ImportToken.Heading }
		if (headings.isEmpty()) {
			return buildImportPreview(tokens, 1, wrapTopLevelInGroups, fallbackName)
		}

		val levels = headings.map { (it.value as ImportToken.Heading).level }
		val minLevel = levels.min()
		val firstHeading = headings.first().value as ImportToken.Heading
		val titleIndex = if (firstHeading.level == minLevel && levels.count { it == minLevel } == 1) {
			headings.first().index
		} else {
			null
		}

		val title = titleIndex?.let { (tokens[it] as ImportToken.Heading).title }
		val workingTokens = if (titleIndex != null) {
			tokens.filterIndexed { index, _ -> index != titleIndex }
		} else {
			tokens
		}

		val sceneLevel = workingTokens
			.filterIsInstance<ImportToken.Heading>()
			.groupingBy { it.level }
			.eachCount()
			.entries
			// Most headings wins (chapters outnumber parts); ties resolve to the shallower level.
			.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
			.firstOrNull()?.key ?: 1

		return buildImportPreview(workingTokens, sceneLevel, wrapTopLevelInGroups, fallbackName)
			.copy(title = title)
	}

	/**
	 * Bold-only lines are a last resort: they are only accepted when heading markup produced at most
	 * a story title, and only when several of them agree, so a bolded aside inside a properly headed
	 * manuscript can never restructure it. They sit one level below the title so that Auto still
	 * reads that title as the story name.
	 */
	private fun tokenizeByMarkup(lines: List<String>, detectBoldLines: Boolean): List<ImportToken> {
		val tokens = tokenizeHeadingMarkup(lines, boldLevel = null)
		val markupHeadings = tokens.filterIsInstance<ImportToken.Heading>()
		if (!detectBoldLines || markupHeadings.size >= MIN_MARKUP_HEADINGS) return tokens

		val boldLevel = ((markupHeadings.minOfOrNull { it.level } ?: 0) + 1).coerceAtMost(MAX_HEADING_LEVEL)
		val withBold = tokenizeHeadingMarkup(lines, boldLevel)
		val boldFound = withBold.count { it is ImportToken.Heading } - markupHeadings.size
		return if (boldFound >= MIN_BOLD_HEADINGS) withBold else tokens
	}

	private fun tokenizeHeadingMarkup(lines: List<String>, boldLevel: Int?): List<ImportToken> {
		val tokens = mutableListOf<ImportToken>()
		var i = 0
		while (i < lines.size) {
			val line = lines[i]
			val atxLevel = atxHeadingLevel(line)
			val underlineLevel = lines.getOrNull(i + 1)?.let { setextUnderlineLevel(it) }
			when {
				atxLevel != null -> tokens.add(headingToken(atxLevel, line))

				underlineLevel != null && isSetextTitle(lines, i, underlineLevel) -> {
					tokens.add(headingToken(underlineLevel, line))
					i++
				}

				boldLevel != null && isBoldOnlyLine(line) -> tokens.add(headingToken(boldLevel, line))

				else -> tokens.add(ImportToken.Body(line))
			}
			i++
		}
		return tokens
	}

	private fun tokenizeByPattern(lines: List<String>, pattern: String): List<ImportToken> {
		val regex = if (pattern.isBlank()) null else runCatching { Regex(pattern) }.getOrNull()
		val tokens = mutableListOf<ImportToken>()
		var i = 0
		while (i < lines.size) {
			val line = lines[i]
			val plain = plainText(line)
			if (regex != null && plain.isNotBlank() && regex.containsMatchIn(plain)) {
				tokens.add(headingToken(1, line))
				// The underline belongs to the title it underlines, not to the scene body.
				if (lines.getOrNull(i + 1)?.let { setextUnderlineLevel(it) } != null) i++
			} else {
				tokens.add(ImportToken.Body(line))
			}
			i++
		}
		return tokens
	}

	private fun headingToken(level: Int, line: String) =
		ImportToken.Heading(level, sanitizeImportName(plainText(line)), raw = line)

	private fun atxHeadingLevel(line: String): Int? {
		val body = line.trimStart(' ')
		if (line.length - body.length > MAX_HEADING_INDENT) return null
		val level = body.takeWhile { it == '#' }.length
		return if (level in 1..MAX_HEADING_LEVEL) level else null
	}

	/** `===` underlines a level 1 heading, `---` a level 2 one; any other line is not an underline. */
	private fun setextUnderlineLevel(line: String): Int? {
		val body = line.trimEnd().trimStart(' ')
		if (line.trimEnd().length - body.length > MAX_HEADING_INDENT) return null
		val marker = body.firstOrNull() ?: return null
		if (marker != '=' && marker != '-') return null
		if (body.any { it != marker }) return null
		return if (marker == '=') 1 else 2
	}

	/**
	 * A `---` underline is also the spelling of a scene-break rule and of a front-matter fence, so it
	 * only makes a heading of the line above when that line opens a paragraph. `===` is unambiguous.
	 */
	private fun isSetextTitle(lines: List<String>, index: Int, underlineLevel: Int): Boolean {
		val line = lines[index]
		if (line.isBlank()) return false
		if (line.length - line.trimStart(' ').length > MAX_HEADING_INDENT) return false
		if (setextUnderlineLevel(line) != null) return false
		return underlineLevel == 1 || index == 0 || lines[index - 1].isBlank()
	}

	/** True when the line is nothing but one short bold span. */
	private fun isBoldOnlyLine(line: String): Boolean {
		val trimmed = line.trim()
		return BOLD_MARKERS.any { marker ->
			trimmed.length > marker.length * 2 &&
				trimmed.startsWith(marker) &&
				trimmed.endsWith(marker) &&
				trimmed.substring(marker.length, trimmed.length - marker.length).let {
					it.isNotBlank() && !it.contains(marker) && wordCount(it) <= HEADING_MAX_WORDS
				}
		}
	}

	/** A line with its heading markers and wrapping emphasis removed. */
	private fun plainText(line: String): String {
		var text = if (atxHeadingLevel(line) != null) line.trimStart(' ').trimStart('#').trim() else line.trim()
		for (marker in EMPHASIS_MARKERS) {
			while (text.length > marker.length * 2 && text.startsWith(marker) && text.endsWith(marker)) {
				text = text.substring(marker.length, text.length - marker.length).trim()
			}
		}
		return text
	}

	companion object {
		private const val MAX_HEADING_LEVEL = 6
		private const val MAX_HEADING_INDENT = 3
		private const val MIN_MARKUP_HEADINGS = 2
		private const val MIN_BOLD_HEADINGS = 2
		private const val BOM = "﻿"
		private val BOLD_MARKERS = listOf("**", "__")
		private val EMPHASIS_MARKERS = listOf("**", "__", "*", "_")
	}
}
