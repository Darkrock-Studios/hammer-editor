package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.RtfSplitStrategy
import com.darkrockstudios.libs.rtfparserkmp.parser.parseRtf
import com.darkrockstudios.libs.rtfparserkmp.rtf.Command
import com.darkrockstudios.libs.rtfparserkmp.rtf.CommandType

/**
 * Imports an RTF document. The library converts character formatting (bold/italic) to Markdown but
 * never emits headings, so this importer collects each paragraph with its formatting and decides
 * scene/chapter breaks itself per [ImportOptions.rtfSplitStrategy]. The resulting heading/body
 * stream feeds the shared [buildImportPreview] folding, so RTF reuses the same scene/group structure
 * (and the "create chapter groups" option) as Markdown import.
 */
class RtfStoryImporter : StoryImporter {
	override val format: ImportFormat = ImportFormat.Rtf

	override fun preview(
		sourceName: String,
		content: ByteArray,
		options: ImportOptions,
	): ImportPreview {
		val paragraphs = collectParagraphs(content)

		val tokens = when (options.rtfSplitStrategy) {
			RtfSplitStrategy.Formatting -> tokensByFormatting(paragraphs)
			RtfSplitStrategy.Pattern -> tokensByPattern(paragraphs, options.rtfChapterPattern)
			RtfSplitStrategy.SingleScene -> bodyTokens(paragraphs)
		}

		val sceneLevel = tokens
			.filterIsInstance<ImportToken.Heading>()
			.maxOfOrNull { it.level } ?: 1

		return buildImportPreview(
			tokens = tokens,
			sceneLevel = sceneLevel,
			wrapTopLevelInGroups = options.createChapterGroups,
			fallbackName = sanitizeImportName(sourceName),
		)
	}

	private fun collectParagraphs(content: ByteArray): List<RtfParagraph> {
		val collector = RtfParagraphCollector()
		parseRtf(content, collector)
		return collector.paragraphs
	}

	private fun tokensByFormatting(paragraphs: List<RtfParagraph>): List<ImportToken> {
		val headingLevels = headingLevelsByFormatting(paragraphs)
		return tokenize(paragraphs) { headingLevels[it] }
	}

	/** Maps each formatting-detected heading to its level (1..2); non-heading paragraphs are absent. */
	private fun headingLevelsByFormatting(paragraphs: List<RtfParagraph>): Map<RtfParagraph, Int> {
		val bodySize = bodyFontSize(paragraphs)
		val tierByHeading = paragraphs
			.filter { it.isHeading(bodySize) }
			.associateWith { it.tierKey(bodySize) }

		// Highest tier (largest font / lowest outline level) sorts first, capped at two levels.
		val tiers = tierByHeading.values.distinct().sorted()
		return tierByHeading.mapValues { (_, tier) -> tiers.indexOf(tier).coerceAtMost(1) + 1 }
	}

	private fun tokensByPattern(paragraphs: List<RtfParagraph>, pattern: String): List<ImportToken> {
		val regex = if (pattern.isBlank()) null else runCatching { Regex(pattern) }.getOrNull()
		return tokenize(paragraphs) { p ->
			if (regex != null && regex.containsMatchIn(p.plainText)) 1 else null
		}
	}

	private fun bodyTokens(paragraphs: List<RtfParagraph>): List<ImportToken> =
		tokenize(paragraphs) { null }

	/**
	 * Folds paragraphs into tokens: a blank paragraph becomes a blank body line, a paragraph that
	 * [headingLevel] resolves to a level becomes a heading at that level, and anything else is body
	 * prose. The split strategies differ only in [headingLevel].
	 */
	private fun tokenize(
		paragraphs: List<RtfParagraph>,
		headingLevel: (RtfParagraph) -> Int?,
	): List<ImportToken> = buildList {
		for (p in paragraphs) {
			val level = if (p.isBlank) null else headingLevel(p)
			when {
				p.isBlank -> add(ImportToken.Body(""))
				level != null -> add(headingToken(p, level))
				else -> addBody(p)
			}
		}
	}

	private fun MutableList<ImportToken>.addBody(p: RtfParagraph) {
		add(ImportToken.Body(p.markdown))
		// Blank line so adjacent paragraphs stay separate paragraphs in the scene's Markdown.
		add(ImportToken.Body(""))
	}

	private fun headingToken(p: RtfParagraph, level: Int): ImportToken.Heading {
		val title = sanitizeImportName(p.plainText)
		return ImportToken.Heading(level = level, title = title, raw = p.plainText)
	}

	private fun bodyFontSize(paragraphs: List<RtfParagraph>): Int? {
		val weightBySize = HashMap<Int, Int>()
		for (p in paragraphs) {
			val size = p.dominantFontSize ?: continue
			weightBySize[size] = (weightBySize[size] ?: 0) + p.plainText.length
		}
		return weightBySize.maxByOrNull { it.value }?.key
	}

	private fun RtfParagraph.isHeading(bodySize: Int?): Boolean {
		if (isBlank) return false
		if (isOutlineHeading) return true
		if (wordCount > HEADING_MAX_WORDS) return false
		if (bodySize != null && dominantFontSize != null && dominantFontSize > bodySize) return true
		return mostlyBold
	}

	private fun RtfParagraph.tierKey(bodySize: Int?): Int = when {
		isOutlineHeading -> outlineLevel!!
		dominantFontSize != null && bodySize != null && dominantFontSize > bodySize -> SIZE_TIER_BASE - dominantFontSize
		else -> BOLD_TIER
	}

	// Outline levels 0..8 are Heading 1..9; level 9 is "body text" and must not count as a heading.
	private val RtfParagraph.isOutlineHeading: Boolean
		get() = outlineLevel != null && outlineLevel in 0..MAX_OUTLINE_HEADING_LEVEL

	private companion object {
		const val HEADING_MAX_WORDS = 12
		const val MAX_OUTLINE_HEADING_LEVEL = 8
		const val SIZE_TIER_BASE = 1000
		const val BOLD_TIER = 10_000
	}

	private class RtfParagraph(
		val markdown: String,
		val plainText: String,
		val dominantFontSize: Int?,
		val mostlyBold: Boolean,
		val outlineLevel: Int?,
	) {
		val isBlank: Boolean get() = plainText.isBlank()
		val wordCount: Int get() = if (isBlank) 0 else plainText.trim().split(WHITESPACE).size

		private companion object {
			val WHITESPACE = Regex("\\s+")
		}
	}

	/**
	 * Walks the body destinations of an RTF document, emitting one [RtfParagraph] per `\par`. Tracks
	 * group-scoped character formatting (bold/italic/font size) and paragraph properties (outline
	 * level), wrapping bold/italic runs in Markdown. Delimiters hug the emphasized text: RTF writers
	 * routinely put the surrounding spaces inside the run, and CommonMark rejects emphasis with
	 * whitespace immediately inside the markers.
	 */
	private class RtfParagraphCollector : com.darkrockstudios.libs.rtfparserkmp.parser.RtfListener {
		val paragraphs = mutableListOf<RtfParagraph>()

		private var currentDestination = Command.rtf
		private val destinationStack = ArrayDeque<Command>()

		private var bold = false
		private var italic = false
		private var fontSize: Int? = null
		private val formattingStack = ArrayDeque<Formatting>()

		private var outlineLevel: Int? = null

		private val markdown = StringBuilder()
		private val plain = StringBuilder()
		private val openMarkers = ArrayDeque<Marker>()
		private val sizeCounts = HashMap<Int, Int>()
		private var boldChars = 0
		private var totalChars = 0

		override fun processGroupStart() {
			destinationStack.addLast(currentDestination)
			formattingStack.addLast(Formatting(bold, italic, fontSize))
		}

		override fun processGroupEnd() {
			currentDestination = destinationStack.removeLast()
			val restored = formattingStack.removeLast()
			bold = restored.bold
			italic = restored.italic
			fontSize = restored.fontSize
			closeDisabledMarkers()
		}

		override fun processString(string: String) {
			if (!isBodyDestination()) return
			plain.append(string)
			appendMarkdown(string)
			for (ch in string) {
				if (ch.isWhitespace()) continue
				totalChars++
				if (bold) boldChars++
				fontSize?.let { sizeCounts[it] = (sizeCounts[it] ?: 0) + 1 }
			}
		}

		override fun processCommand(command: Command, parameter: Int, hasParameter: Boolean, optional: Boolean) {
			if (command.commandType == CommandType.Destination) {
				currentDestination = command
			}

			when (command) {
				Command.b -> setBold(!hasParameter || parameter != 0)
				Command.i -> setItalic(!hasParameter || parameter != 0)
				Command.fs -> if (hasParameter) fontSize = parameter
				Command.outlinelevelN -> outlineLevel = if (hasParameter) parameter else 0
				Command.plain -> {
					setBold(false)
					setItalic(false)
				}
				Command.pard -> outlineLevel = null
				Command.par, Command.row -> if (isBodyDestination()) endParagraph()
				Command.line -> if (isBodyDestination()) {
					closeEmphasis()
					markdown.append("  \n")
					plain.append('\n')
				}
				Command.tab, Command.cell -> if (isBodyDestination()) {
					markdown.append('\t')
					plain.append('\t')
				}
				else -> {}
			}
		}

		override fun processDocumentEnd() {
			endParagraph()
		}

		private fun endParagraph() {
			closeEmphasis()
			val md = escapeOrderedListMarkers(markdown.toString().trim())
			val text = plain.toString().trim()
			val dominantSize = sizeCounts.maxByOrNull { it.value }?.key
			val mostlyBold = totalChars > 0 && boldChars.toDouble() / totalChars >= BOLD_RATIO
			paragraphs.add(
				RtfParagraph(
					markdown = md,
					plainText = text,
					dominantFontSize = dominantSize,
					mostlyBold = mostlyBold,
					outlineLevel = outlineLevel,
				)
			)

			markdown.clear()
			plain.clear()
			openMarkers.clear()
			sizeCounts.clear()
			boldChars = 0
			totalChars = 0
		}

		private fun isBodyDestination(): Boolean =
			currentDestination == Command.rtf ||
				currentDestination == Command.pntext ||
				currentDestination == Command.fldrslt

		private fun setBold(value: Boolean) {
			if (value != bold) {
				bold = value
				if (!value) closeDisabledMarkers()
			}
		}

		private fun setItalic(value: Boolean) {
			if (value != italic) {
				italic = value
				if (!value) closeDisabledMarkers()
			}
		}

		private fun isActive(marker: Marker): Boolean = when (marker) {
			Marker.BOLD -> bold
			Marker.ITALIC -> italic
		}

		/**
		 * Opens pending emphasis at the first non-whitespace character, so a run's leading whitespace
		 * stays outside the delimiters and an all-whitespace run opens nothing.
		 */
		private fun appendMarkdown(text: String) {
			val firstVisible = text.indexOfFirst { !it.isWhitespace() }
			if (firstVisible < 0) {
				markdown.append(text)
				return
			}
			markdown.append(text.substring(0, firstVisible))
			openEmphasis()
			markdown.append(escape(text.substring(firstVisible)))
		}

		private fun openEmphasis() {
			if (bold && Marker.BOLD !in openMarkers) {
				markdown.append(Marker.BOLD.text)
				openMarkers.addLast(Marker.BOLD)
			}
			if (italic && Marker.ITALIC !in openMarkers) {
				markdown.append(Marker.ITALIC.text)
				openMarkers.addLast(Marker.ITALIC)
			}
		}

		/**
		 * Closes every marker down to the shallowest inactive one. Still-active markers come off the
		 * stack too and are reopened by [openEmphasis] at the next non-whitespace character.
		 */
		private fun closeDisabledMarkers() {
			val deepestDisabled = openMarkers.indexOfFirst { !isActive(it) }
			if (deepestDisabled < 0) return
			while (openMarkers.size > deepestDisabled) {
				closeMarker(openMarkers.removeLast())
			}
		}

		private fun closeEmphasis() {
			while (openMarkers.isNotEmpty()) {
				closeMarker(openMarkers.removeLast())
			}
		}

		/** Emits [marker] ahead of any trailing whitespace, keeping the delimiter against the text. */
		private fun closeMarker(marker: Marker) {
			markdown.insert(markdown.length - trailingWhitespaceLength(), marker.text)
		}

		private fun trailingWhitespaceLength(): Int =
			markdown.length - (markdown.indexOfLast { !it.isWhitespace() } + 1)

		/** Keeps an imported paragraph that starts with "1." from parsing as an ordered list. */
		private fun escapeOrderedListMarkers(text: String): String =
			text.replace(ORDERED_LIST_MARKER, "$1\\\\.")

		private fun escape(text: String): String {
			val out = StringBuilder(text.length)
			for (ch in text) {
				if (ch in MARKDOWN_SPECIAL) out.append('\\')
				out.append(ch)
			}
			return out.toString()
		}

		private class Formatting(val bold: Boolean, val italic: Boolean, val fontSize: Int?)

		private enum class Marker(val text: String) {
			BOLD("**"),
			ITALIC("*"),
		}

		private companion object {
			const val BOLD_RATIO = 0.8

			/** Mirrors `MARKDOWN_SPECIAL_CHARS` in ComposeTextEditor's Markdown escaper. */
			val MARKDOWN_SPECIAL = "\\`*_{}[]()<>#+-!|".toSet()
			val ORDERED_LIST_MARKER = Regex("^(\\d+)\\.", RegexOption.MULTILINE)
		}
	}
}
