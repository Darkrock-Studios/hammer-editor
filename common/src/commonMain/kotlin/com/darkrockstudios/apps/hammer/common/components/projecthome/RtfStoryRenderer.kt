package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfAlignment
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBlock
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBookmark
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfBorder
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfColor
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfDocument
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfDocumentWriter
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfFont
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfFontFamily
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfHyperlink
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfHyperlinkKind
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfInfo
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfInline
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfPageBreak
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfParagraph
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfParagraphStyle
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfSpanStyle
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfTab
import com.darkrockstudios.libs.rtfparserkmp.writer.RtfTextRun
import okio.BufferedSink

private val BODY_FONT = RtfFont(EXPORT_BODY_FONT, RtfFontFamily.Roman)
private val MONO_FONT = RtfFont(EXPORT_MONO_FONT, RtfFontFamily.Modern)

// Font sizes in half-points; the body is 12pt to match the other exporters.
private const val BODY_HALF_POINTS = 24
private const val TITLE_HALF_POINTS = 72
private const val AUTHOR_HALF_POINTS = 32
private const val TOC_TITLE_HALF_POINTS = 48

// Paragraph spacing/indent in twips (twentieths of a point); 1440 twips = 1 inch.
private const val BODY_FIRST_LINE_INDENT = 360
private const val PARAGRAPH_SPACE_AFTER = 160
private const val HEADING_SPACE_BEFORE = 360
private const val TITLE_SPACE_BEFORE = 3600
private const val SECTION_SPACE = 240
private const val QUOTE_INDENT = 720
private const val LIST_INDENT_PER_LEVEL = 360

/**
 * Renders the story as a Rich Text Format (.rtf) document mirroring the EPUB/DOCX layout: a title
 * page, a Contents page of links to chapter bookmarks, then one chapter per top-level node starting
 * on a fresh page. Chapter bodies are rendered from markdown into formatted RTF runs. Heading and
 * link colors pick up the project theme's accent colors.
 *
 * The document is assembled as a strongly-typed [RtfDocument] and serialized by [RtfDocumentWriter],
 * which keeps the output 7-bit ASCII (non-ASCII as `\uN` escapes) and portable across readers.
 */
fun writeStoryAsRtf(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }
	val primary = themeColor(projectData.theme?.primary)
	val secondary = themeColor(projectData.theme?.secondary)

	val blocks = buildList {
		addAll(titlePage(projectName, strings.authorByline, primary))
		addAll(contentsPage(effective, strings.contentsTitle, primary))
		effective.forEachIndexed { index, chapter -> addAll(chapterBlocks(index, chapter, primary, secondary)) }
	}

	val document = RtfDocument(
		blocks = blocks,
		defaultFont = BODY_FONT,
		defaultFontSizeHalfPoints = BODY_HALF_POINTS,
		info = RtfInfo(title = projectName, author = authorName),
		generator = "Hammer ${BuildMetadata.APP_VERSION}",
	)

	sink.writeUtf8(RtfDocumentWriter().write(document))
}

private fun titlePage(projectName: String, authorByline: String?, primary: RtfColor?): List<RtfBlock> = buildList {
	add(
		RtfParagraph(
			content = listOf(
				RtfTextRun(
					projectName,
					RtfSpanStyle(bold = true, fontSizeHalfPoints = TITLE_HALF_POINTS, color = primary),
				),
			),
			style = RtfParagraphStyle(
				alignment = RtfAlignment.Center,
				spaceBeforeTwips = TITLE_SPACE_BEFORE,
				spaceAfterTwips = SECTION_SPACE,
			),
		),
	)
	if (authorByline != null) {
		add(
			RtfParagraph(
				content = listOf(
					RtfTextRun(authorByline, RtfSpanStyle(italic = true, fontSizeHalfPoints = AUTHOR_HALF_POINTS)),
				),
				style = RtfParagraphStyle(alignment = RtfAlignment.Center, spaceAfterTwips = SECTION_SPACE),
			),
		)
	}
}

private fun contentsPage(chapters: List<StoryChapter>, contentsTitle: String, primary: RtfColor?): List<RtfBlock> = buildList {
	add(RtfPageBreak)
	add(
		RtfParagraph(
			content = listOf(RtfTextRun(contentsTitle, RtfSpanStyle(bold = true, fontSizeHalfPoints = TOC_TITLE_HALF_POINTS))),
			style = RtfParagraphStyle(
				alignment = RtfAlignment.Center,
				spaceBeforeTwips = SECTION_SPACE,
				spaceAfterTwips = SECTION_SPACE,
			),
		),
	)
	chapters.forEachIndexed { index, chapter ->
		val label = "${index + 1}. ${chapter.name}"
		add(
			RtfParagraph(
				content = listOf(
					RtfHyperlink(
						target = chapterBookmark(index),
						kind = RtfHyperlinkKind.Bookmark,
						content = listOf(RtfTextRun(label, RtfSpanStyle(underline = true, color = primary))),
					),
				),
				style = RtfParagraphStyle(spaceAfterTwips = PARAGRAPH_SPACE_AFTER),
			),
		)
	}
}

private fun chapterBlocks(
	index: Int,
	chapter: StoryChapter,
	primary: RtfColor?,
	secondary: RtfColor?,
): List<RtfBlock> = buildList {
	add(RtfPageBreak)
	add(
		RtfParagraph(
			content = listOf(
				RtfBookmark(
					name = chapterBookmark(index),
					content = listOf(
						RtfTextRun(
							"${index + 1}. ${chapter.name}",
							RtfSpanStyle(bold = true, fontSizeHalfPoints = HEADING_HALF_POINTS[0], color = primary),
						),
					),
				),
			),
			style = RtfParagraphStyle(
				spaceBeforeTwips = HEADING_SPACE_BEFORE,
				spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
				keepWithNext = true,
			),
		),
	)
	if (chapter.markdown.isNotBlank()) {
		addAll(renderMarkdownRtf(chapter.markdown, primary, secondary))
	}
}

/** Project themes store colors as ARGB hex (`#FFRRGGBB`); convert to an [RtfColor]. */
private fun themeColor(argb: String?): RtfColor? {
	val css = argb?.let(::argbHexToCssHex)?.removePrefix("#") ?: return null
	val value = css.toIntOrNull(16) ?: return null
	return RtfColor.fromRgb(value)
}

/**
 * Renders a chapter's markdown into [RtfBlock]s by consuming the shared [parseProseMarkdown] model.
 * Block kinds map onto paragraph properties; inline spans become styled [RtfTextRun]s, with
 * consecutive same-link spans grouped into a single [RtfHyperlink]. Hard line breaks ride along as
 * `\n` in run text — the rtf-writer escaper turns those into `\line`.
 */
private fun renderMarkdownRtf(markdown: String, primary: RtfColor?, secondary: RtfColor?): List<RtfBlock> {
	val blocks = mutableListOf<RtfBlock>()
	for (block in parseProseMarkdown(markdown)) {
		when (block) {
			is ProseBlock.Paragraph -> blocks += bodyParagraph(block.spans, primary)
			is ProseBlock.Heading -> blocks += headingParagraph(block, primary, secondary)
			is ProseBlock.Listing -> {
				val numbering = RtfListNumbering()
				block.items.forEach { blocks += listParagraph(it, numbering, primary) }
			}

			is ProseBlock.Quote -> block.paragraphs.forEach { blocks += quoteParagraph(it, primary) }
			is ProseBlock.CodeBlock -> codeBlockLines(block.code).forEach { blocks += codeParagraph(it) }
			ProseBlock.Rule -> blocks += ruleParagraph()
			is ProseBlock.Table -> blocks += tableParagraphs(block, primary)
		}
	}
	return blocks
}

private fun bodyParagraph(spans: List<ProseSpan>, primary: RtfColor?): RtfParagraph = RtfParagraph(
	content = coalesceRuns(spansToInlines(spans, RtfSpanStyle.Default, primary)),
	style = RtfParagraphStyle(firstLineIndentTwips = BODY_FIRST_LINE_INDENT, spaceAfterTwips = PARAGRAPH_SPACE_AFTER),
)

private fun headingParagraph(block: ProseBlock.Heading, primary: RtfColor?, secondary: RtfColor?): RtfParagraph {
	val halfPoints = HEADING_HALF_POINTS[(block.level - 1).coerceIn(0, HEADING_HALF_POINTS.lastIndex)]
	val color = when (block.level) {
		1 -> primary
		2 -> secondary
		else -> null
	}
	val style = RtfSpanStyle(bold = true, fontSizeHalfPoints = halfPoints, color = color)
	return RtfParagraph(
		content = coalesceRuns(spansToInlines(block.spans, style, primary)),
		style = RtfParagraphStyle(
			spaceBeforeTwips = HEADING_SPACE_BEFORE,
			spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
			keepWithNext = true,
		),
	)
}

private fun listParagraph(item: ProseListItem, numbering: RtfListNumbering, primary: RtfColor?): RtfParagraph {
	val content = buildList {
		add(RtfTextRun(numbering.marker(item), RtfSpanStyle.Default))
		addAll(spansToInlines(item.spans, RtfSpanStyle.Default, primary))
	}
	return RtfParagraph(
		content = coalesceRuns(content),
		style = RtfParagraphStyle(
			leftIndentTwips = LIST_INDENT_PER_LEVEL * (item.level + 1),
			firstLineIndentTwips = -LIST_INDENT_PER_LEVEL,
			spaceAfterTwips = PARAGRAPH_SPACE_AFTER,
		),
	)
}

private fun quoteParagraph(spans: List<ProseSpan>, primary: RtfColor?): RtfParagraph = RtfParagraph(
	content = coalesceRuns(spansToInlines(spans, RtfSpanStyle(italic = true), primary)),
	style = RtfParagraphStyle(leftIndentTwips = QUOTE_INDENT, spaceAfterTwips = PARAGRAPH_SPACE_AFTER),
)

private fun codeParagraph(line: String): RtfParagraph = RtfParagraph(
	content = listOf(RtfTextRun(line, RtfSpanStyle(font = MONO_FONT))),
	style = RtfParagraphStyle(spaceAfterTwips = 0),
)

private fun ruleParagraph(): RtfParagraph = RtfParagraph(
	content = emptyList(),
	style = RtfParagraphStyle(spaceAfterTwips = PARAGRAPH_SPACE_AFTER, bottomBorder = RtfBorder()),
)

/** Tab-separated text fallback for GFM pipe tables, which RTF has no real table primitive for yet. */
private fun tableParagraphs(block: ProseBlock.Table, primary: RtfColor?): List<RtfBlock> =
	(listOf(block.header) + block.rows).map { row ->
		val content = buildList {
			row.forEachIndexed { index, cell ->
				if (index > 0) add(RtfTab)
				addAll(spansToInlines(cell, RtfSpanStyle.Default, primary))
			}
		}
		RtfParagraph(coalesceRuns(content), RtfParagraphStyle(spaceAfterTwips = PARAGRAPH_SPACE_AFTER))
	}

/** Maps prose spans to RTF inlines, grouping consecutive same-link spans into one [RtfHyperlink]. */
private fun spansToInlines(spans: List<ProseSpan>, base: RtfSpanStyle, primary: RtfColor?): List<RtfInline> {
	val out = mutableListOf<RtfInline>()
	var i = 0
	while (i < spans.size) {
		val link = spans[i].link
		if (link != null) {
			val linkStyle = base.copy(underline = true, color = primary)
			val content = mutableListOf<RtfInline>()
			while (i < spans.size && spans[i].link == link) {
				content += runFor(spans[i], linkStyle)
				i++
			}
			out += RtfHyperlink(target = link, content = content)
		} else {
			out += runFor(spans[i], base)
			i++
		}
	}
	return out
}

private fun runFor(span: ProseSpan, base: RtfSpanStyle): RtfTextRun = RtfTextRun(
	text = span.text,
	style = base.copy(
		bold = base.bold || span.bold,
		italic = base.italic || span.italic,
		strikethrough = base.strikethrough || span.strikethrough,
		font = if (span.code) MONO_FONT else base.font,
	),
)

/** Merges consecutive [RtfTextRun]s that share a style so plain prose stays in a single run. */
private fun coalesceRuns(inlines: List<RtfInline>): List<RtfInline> {
	val result = mutableListOf<RtfInline>()
	for (inline in inlines) {
		val last = result.lastOrNull()
		if (inline is RtfTextRun && last is RtfTextRun && last.style == inline.style) {
			result[result.lastIndex] = last.copy(text = last.text + inline.text)
		} else {
			result.add(inline)
		}
	}
	return result
}

/**
 * Per-level counters for the literal ordered-list numbers RTF must write itself. Items arrive
 * flattened in reading order with a [ProseListItem.level]; descending starts a fresh counter,
 * ascending continues the shallower list and drops the deeper ones.
 */
private class RtfListNumbering {
	private val counters = mutableListOf<Int>()
	private var lastLevel = -1

	fun marker(item: ProseListItem): String {
		val level = item.level
		when {
			level > lastLevel -> {
				while (counters.size <= level) counters.add(0)
				counters[level] = 1
			}

			level == lastLevel -> counters[level]++
			else -> {
				while (counters.size > level + 1) counters.removeAt(counters.lastIndex)
				counters[level]++
			}
		}
		lastLevel = level
		return if (item.ordered) "${counters[level]}.\t" else "•\t"
	}
}
