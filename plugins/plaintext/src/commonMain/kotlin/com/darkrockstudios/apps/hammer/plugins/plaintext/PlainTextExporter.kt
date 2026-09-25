package com.darkrockstudios.apps.hammer.plugins.plaintext

import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ProseBlock
import com.darkrockstudios.apps.hammer.common.data.export.ProseListItem
import com.darkrockstudios.apps.hammer.common.data.export.ProseSpan
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.parseProseMarkdown
import okio.BufferedSink

class PlainTextExporter(private val settings: () -> PlainTextSettings) : StoryExporter {
	override val formatId = FORMAT_ID
	override val fileExtension = "txt"
	override val mimeType = "text/plain"
	override val needsProjectData = false

	override fun render(sink: BufferedSink, input: ExportInput) {
		sink.writeUtf8(renderPlainText(input, settings()))
	}

	companion object {
		const val FORMAT_ID = "${PlainTextPlugin.ID}.txt"
	}
}

private const val INDENT = "\t"
private const val QUOTE_INDENT = "    "

internal fun renderPlainText(input: ExportInput, settings: PlainTextSettings): String {
	val headings = input.treatTopLevelAsChapters && settings.chapterHeadings
	val chapters = input.bookChapters().map { chapter ->
		// A rule inside a scene is the author's own break, so it separates passages like a scene boundary.
		val body = chapter.scenes
			.flatMap { scene -> scenePassages(scene, settings) }
			.filter { it.isNotEmpty() }
			.joinToString(sceneSeparator(settings)) { joinParagraphs(it, settings) }
		if (headings) "${chapter.name}\n\n$body" else body
	}
	return chapters.filter { it.isNotBlank() }.joinToString("\n\n\n\n").trimEnd() + "\n"
}

private fun sceneSeparator(settings: PlainTextSettings): String =
	if (settings.sceneBreak == SceneBreak.Blank) "\n\n\n" else "\n\n${settings.sceneBreak.marker}\n\n"

private fun joinParagraphs(paragraphs: List<String>, settings: PlainTextSettings): String =
	when (settings.paragraphs) {
		ParagraphStyle.BlankLine -> paragraphs.joinToString("\n\n")
		ParagraphStyle.Indented -> paragraphs.joinToString("\n") { paragraph ->
			paragraph.lines().joinToString("\n") { INDENT + it }
		}
	}

/** A scene's paragraphs, split into passages at rules. The author's blank lines are dropped: [ParagraphStyle] alone decides paragraph spacing. */
private fun scenePassages(markdown: String, settings: PlainTextSettings): List<List<String>> {
	val passages = mutableListOf(mutableListOf<String>())
	parseProseMarkdown(markdown).forEach { block ->
		if (block == ProseBlock.Rule) {
			passages += mutableListOf<String>()
		} else {
			passages.last() += blockParagraphs(block, settings).filter { it.isNotBlank() }
		}
	}
	return passages
}

private fun blockParagraphs(block: ProseBlock, settings: PlainTextSettings): List<String> = when (block) {
	is ProseBlock.Paragraph -> listOf(spansText(block.spans, settings))
	ProseBlock.Blank, ProseBlock.Rule -> emptyList()
	is ProseBlock.Heading -> listOf(spansText(block.spans, settings))
	is ProseBlock.Listing -> listOf(listText(block.items, settings))
	is ProseBlock.Quote -> block.paragraphs.map { QUOTE_INDENT + spansText(it, settings) }
	is ProseBlock.CodeBlock -> listOf(block.code.trimEnd())
	is ProseBlock.Table -> listOf(
		(listOf(block.header) + block.rows).joinToString("\n") { row ->
			row.joinToString(" | ") { cell -> spansText(cell, settings) }
		}
	)
}

private fun listText(items: List<ProseListItem>, settings: PlainTextSettings): String {
	// One counter per nesting level; a shallower item ends the deeper lists under it.
	val counters = mutableListOf<Int>()
	return items.joinToString("\n") { item ->
		while (counters.size > item.level + 1) counters.removeAt(counters.lastIndex)
		while (counters.size < item.level + 1) counters += 0
		counters[item.level] += 1
		val marker = if (item.ordered) "${counters[item.level]}." else "-"
		"  ".repeat(item.level) + "$marker " + spansText(item.spans, settings)
	}
}

/** Consecutive italic spans are wrapped once, so bold or a link inside an italic run does not split it. */
private fun spansText(spans: List<ProseSpan>, settings: PlainTextSettings): String {
	val marker = when (settings.italics) {
		Italics.Underscores -> "_"
		Italics.Asterisks -> "*"
		Italics.Dropped -> ""
	}
	val out = StringBuilder()
	var run = StringBuilder()
	fun flushRun() {
		if (run.isNotEmpty()) out.append(if (run.isNotBlank() && marker.isNotEmpty()) wrapTrimmed(run.toString(), marker) else run)
		run = StringBuilder()
	}
	spans.forEach { span ->
		if (span.italic) {
			run.append(span.text)
		} else {
			flushRun()
			out.append(span.text)
		}
	}
	flushRun()
	return out.toString()
}

/** Keeps a run's edge whitespace outside the markers, so `_word_` never becomes `_word _`. */
private fun wrapTrimmed(text: String, marker: String): String {
	val leading = text.takeWhile { it.isWhitespace() }
	val trailing = text.takeLastWhile { it.isWhitespace() }
	return leading + marker + text.trim() + marker + trailing
}
