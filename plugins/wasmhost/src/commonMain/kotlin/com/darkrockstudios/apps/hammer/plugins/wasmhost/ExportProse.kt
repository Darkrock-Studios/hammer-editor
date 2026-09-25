package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.common.data.export.ProseBlock
import com.darkrockstudios.apps.hammer.common.data.export.ProseSpan
import com.darkrockstudios.apps.hammer.common.data.export.parseProseMarkdown
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A scene as an exporter with `input = "prose"` receives it: the host's own reading of its markdown,
 * the one the built-in formats render from, so a plugin needs no markdown parser of its own.
 */
internal fun proseOf(markdown: String): List<ExportBlock> = parseProseMarkdown(markdown).map { block ->
	when (block) {
		is ProseBlock.Paragraph -> ExportBlock.Paragraph(block.spans.map(::ExportSpan))
		ProseBlock.Blank -> ExportBlock.Blank
		is ProseBlock.Heading -> ExportBlock.Heading(block.level, block.spans.map(::ExportSpan))
		is ProseBlock.Listing -> ExportBlock.Listing(
			block.ordered,
			block.items.map { ExportBlock.ListItem(it.level, it.ordered, it.spans.map(::ExportSpan)) },
		)
		is ProseBlock.Quote -> ExportBlock.Quote(block.paragraphs.map { spans -> spans.map(::ExportSpan) })
		is ProseBlock.CodeBlock -> ExportBlock.Code(block.code)
		ProseBlock.Rule -> ExportBlock.Rule
		is ProseBlock.Table -> ExportBlock.Table(
			block.header.map { cell -> cell.map(::ExportSpan) },
			block.rows.map { row -> row.map { cell -> cell.map(::ExportSpan) } },
		)
	}
}

/** Keyed by `type`: paragraph, blank, heading, list, quote, code, rule, or table. */
@Serializable
internal sealed interface ExportBlock {
	/** One line of prose as the author typed it. */
	@Serializable
	@SerialName("paragraph")
	class Paragraph(val spans: List<ExportSpan>) : ExportBlock

	/** A blank line between two passages. */
	@Serializable
	@SerialName("blank")
	data object Blank : ExportBlock

	@Serializable
	@SerialName("heading")
	class Heading(val level: Int, val spans: List<ExportSpan>) : ExportBlock

	/** Nested items are flattened in reading order, each keeping its own depth and marker kind. */
	@Serializable
	@SerialName("list")
	class Listing(val ordered: Boolean, val items: List<ListItem>) : ExportBlock

	@Serializable
	class ListItem(val level: Int, val ordered: Boolean, val spans: List<ExportSpan>)

	@Serializable
	@SerialName("quote")
	class Quote(val paragraphs: List<List<ExportSpan>>) : ExportBlock

	@Serializable
	@SerialName("code")
	class Code(val code: String) : ExportBlock

	/** A horizontal rule: the author's own scene break. */
	@Serializable
	@SerialName("rule")
	data object Rule : ExportBlock

	@Serializable
	@SerialName("table")
	class Table(val header: List<List<ExportSpan>>, val rows: List<List<List<ExportSpan>>>) : ExportBlock
}

/** A run of text in one style. Styles that are off, and a missing link, are left out of the JSON. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal class ExportSpan(
	val text: String,
	@EncodeDefault(EncodeDefault.Mode.NEVER) val bold: Boolean = false,
	@EncodeDefault(EncodeDefault.Mode.NEVER) val italic: Boolean = false,
	@EncodeDefault(EncodeDefault.Mode.NEVER) val strikethrough: Boolean = false,
	@EncodeDefault(EncodeDefault.Mode.NEVER) val code: Boolean = false,
	@EncodeDefault(EncodeDefault.Mode.NEVER) val link: String? = null,
) {
	constructor(span: ProseSpan) : this(span.text, span.bold, span.italic, span.strikethrough, span.code, span.link)
}
