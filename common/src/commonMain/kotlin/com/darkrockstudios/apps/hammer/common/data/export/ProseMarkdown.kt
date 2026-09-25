package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.markdown.ProseHtml
import com.darkrockstudios.apps.hammer.common.data.search.unescapeMarkdown
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/** One contiguous styled run of paragraph text. */
data class ProseSpan(
	val text: String,
	val bold: Boolean = false,
	val italic: Boolean = false,
	val strikethrough: Boolean = false,
	val code: Boolean = false,
	val link: String? = null,
)

/**
 * One item of a [ProseBlock.Listing]. Nested lists are flattened into the parent's [items] in reading
 * order, but each item keeps its own [level] (0-based nesting depth) and [ordered] flag so consumers
 * can reconstruct indentation and markers.
 */
data class ProseListItem(
	val spans: List<ProseSpan>,
	val level: Int,
	val ordered: Boolean,
)

/** A block-level markdown element, reduced to what the prose layout renders. */
sealed interface ProseBlock {
	/** One line of prose as the author typed it. */
	data class Paragraph(val spans: List<ProseSpan>) : ProseBlock

	/** A blank line the author left between two passages. */
	data object Blank : ProseBlock

	data class Heading(val level: Int, val spans: List<ProseSpan>) : ProseBlock
	data class Listing(val ordered: Boolean, val items: List<ProseListItem>) : ProseBlock
	data class Quote(val paragraphs: List<List<ProseSpan>>) : ProseBlock
	data class CodeBlock(val code: String) : ProseBlock
	data object Rule : ProseBlock
	data class Table(val header: List<List<ProseSpan>>, val rows: List<List<List<ProseSpan>>>) : ProseBlock
}

/**
 * Parses [markdown] into renderable blocks using the GFM flavour, so `~~strike~~`, pipe tables, and
 * autolinks are recognized. Unknown syntax degrades to paragraph text.
 *
 * Prose keeps the shape the author gave it, which is what the editor shows them: every newline
 * starts a new [ProseBlock.Paragraph] and every blank line becomes a [ProseBlock.Blank]. CommonMark
 * would reflow both away, turning a page of dialogue into one packed block.
 */
fun parseProseMarkdown(markdown: String): List<ProseBlock> {
	val source = ProseHtml.normalizeLineEndings(markdown)
	val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
	return ProseWalker(source).blocks(root)
}

/** True for the block kinds that make up running prose, as opposed to a structural block. */
internal val ProseBlock.isProseLine: Boolean
	get() = this is ProseBlock.Paragraph || this is ProseBlock.Blank

// ---------------------------------------------------------------------------
// AST walking
// ---------------------------------------------------------------------------

private val HEADING_LEVELS: Map<IElementType, Int> = mapOf(
	MarkdownElementTypes.ATX_1 to 1,
	MarkdownElementTypes.ATX_2 to 2,
	MarkdownElementTypes.ATX_3 to 3,
	MarkdownElementTypes.ATX_4 to 4,
	MarkdownElementTypes.ATX_5 to 5,
	MarkdownElementTypes.ATX_6 to 6,
	MarkdownElementTypes.SETEXT_1 to 1,
	MarkdownElementTypes.SETEXT_2 to 2,
)

private class ProseWalker(private val source: String) {

	/**
	 * Walks the top level, keeping the blank lines between prose passages. A run of newlines
	 * separating two paragraphs is one more than the blank lines it contains; only prose gets them,
	 * because a heading, list or rule carries its own spacing already.
	 */
	fun blocks(root: ASTNode): List<ProseBlock> {
		val out = mutableListOf<ProseBlock>()
		var newlines = 0
		var afterProse = false

		for (child in root.children) {
			if (child.type == MarkdownTokenTypes.EOL) {
				newlines++
				continue
			}
			if (child.type == MarkdownTokenTypes.WHITE_SPACE) continue

			val produced = block(child)
			if (produced.isEmpty()) continue

			if (afterProse && produced.first().isProseLine) {
				val blanks = (newlines - 1).coerceIn(0, ProseHtml.MAX_CONSECUTIVE_BREAKS)
				repeat(blanks) { out += ProseBlock.Blank }
			}
			out += produced
			afterProse = produced.last().isProseLine
			newlines = 0
		}

		return out
	}

	private fun block(node: ASTNode): List<ProseBlock> = when (node.type) {
		MarkdownElementTypes.PARAGRAPH -> paragraphs(node)

		in HEADING_LEVELS -> listOfNotNull(heading(node))

		MarkdownElementTypes.UNORDERED_LIST ->
			listOf(ProseBlock.Listing(ordered = false, items = listItems(node, level = 0, ordered = false)))

		MarkdownElementTypes.ORDERED_LIST ->
			listOf(ProseBlock.Listing(ordered = true, items = listItems(node, level = 0, ordered = true)))

		MarkdownElementTypes.BLOCK_QUOTE ->
			listOfNotNull(quoteParagraphs(node).ifEmpty { null }?.let { ProseBlock.Quote(it) })

		MarkdownElementTypes.CODE_FENCE -> listOf(ProseBlock.CodeBlock(fenceContent(node)))
		MarkdownElementTypes.CODE_BLOCK -> listOf(ProseBlock.CodeBlock(indentedCode(node)))

		MarkdownTokenTypes.HORIZONTAL_RULE -> listOf(ProseBlock.Rule)

		GFMElementTypes.TABLE -> listOfNotNull(table(node))

		// Reference-link definitions produce no output.
		MarkdownElementTypes.LINK_DEFINITION -> emptyList()

		else -> paragraphs(node)
	}

	/** A paragraph node, split into one block per line the author typed. */
	private fun paragraphs(node: ASTNode): List<ProseBlock.Paragraph> =
		if (node.children.isEmpty()) {
			listOfNotNull(inline(node).ifEmpty { null }?.let { ProseBlock.Paragraph(it) })
		} else {
			lines(node).map { ProseBlock.Paragraph(it) }
		}

	/** The inline content of [holder], cut at every newline the author typed. */
	private fun lines(holder: ASTNode): List<List<ProseSpan>> {
		val lines = mutableListOf<List<ProseSpan>>()
		var current = mutableListOf<ProseSpan>()

		fun endLine() {
			normalise(current).ifEmpty { null }?.let { lines += it }
			current = mutableListOf()
		}

		for (child in holder.children) {
			when (child.type) {
				MarkdownTokenTypes.EOL -> endLine()

				// The trailing spaces of a hard break are redundant: the newline itself breaks here.
				MarkdownTokenTypes.HARD_LINE_BREAK -> Unit

				// A quote's own '>' markers sit inside the paragraph, on every line but the first.
				MarkdownTokenTypes.BLOCK_QUOTE -> Unit

				else -> collect(child, Flags(), current)
			}
		}
		endLine()

		return lines
	}

	private fun heading(node: ASTNode): ProseBlock? {
		val content = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT)
			?: node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT)
			?: return null
		val spans = inline(content).ifEmpty { return null }
		return ProseBlock.Heading(level = HEADING_LEVELS.getValue(node.type), spans = spans)
	}

	private fun listItems(listNode: ASTNode, level: Int, ordered: Boolean): List<ProseListItem> {
		val items = mutableListOf<ProseListItem>()
		for (item in listNode.children) {
			if (item.type != MarkdownElementTypes.LIST_ITEM) continue
			val spans = mutableListOf<ProseSpan>()
			fun flush() {
				if (spans.isNotEmpty()) {
					items += ProseListItem(spans.toList(), level = level, ordered = ordered)
					spans.clear()
				}
			}
			for (child in item.children) {
				when (child.type) {
					MarkdownElementTypes.PARAGRAPH -> {
						if (spans.isNotEmpty()) spans += ProseSpan("\n")
						spans += inline(child)
					}
					// Nested lists keep reading order: emit this item's text, then the deeper items.
					MarkdownElementTypes.UNORDERED_LIST -> {
						flush()
						items += listItems(child, level = level + 1, ordered = false)
					}
					MarkdownElementTypes.ORDERED_LIST -> {
						flush()
						items += listItems(child, level = level + 1, ordered = true)
					}
					else -> Unit
				}
			}
			flush()
		}
		return items
	}

	private fun quoteParagraphs(node: ASTNode): List<List<ProseSpan>> {
		val paragraphs = mutableListOf<List<ProseSpan>>()
		for (child in node.children) {
			when (child.type) {
				MarkdownElementTypes.BLOCK_QUOTE -> paragraphs += quoteParagraphs(child)
				MarkdownTokenTypes.BLOCK_QUOTE,
				MarkdownTokenTypes.EOL,
				MarkdownTokenTypes.WHITE_SPACE,
				-> Unit

				// A quoted passage keeps its lines, the same as prose outside the quote.
				else -> paragraphs += lines(child)
			}
		}
		return paragraphs
	}

	private fun fenceContent(node: ASTNode): String {
		val lines = mutableListOf<String>()
		var current: String? = null
		var pastOpeningLine = false
		for (child in node.children) {
			when (child.type) {
				MarkdownTokenTypes.CODE_FENCE_END -> break
				MarkdownTokenTypes.EOL -> {
					if (pastOpeningLine) lines += current.orEmpty()
					pastOpeningLine = true
					current = null
				}

				MarkdownTokenTypes.CODE_FENCE_CONTENT ->
					current = current.orEmpty() + child.getTextInNode(source)

				else -> Unit
			}
		}
		current?.let { lines += it }
		return lines.joinToString("\n")
	}

	private fun indentedCode(node: ASTNode): String =
		node.children
			.filter { it.type == MarkdownTokenTypes.CODE_LINE }
			.joinToString("\n") {
				it.getTextInNode(source).toString().removePrefix("    ").removePrefix("\t")
			}

	private fun table(node: ASTNode): ProseBlock.Table? {
		val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
			?.let { cells(it) } ?: return null
		val rows = node.children
			.filter { it.type == GFMElementTypes.ROW }
			.map { row -> cells(row) }
		return ProseBlock.Table(header = header, rows = rows)
	}

	private fun cells(row: ASTNode): List<List<ProseSpan>> =
		row.children.filter { it.type == GFMTokenTypes.CELL }.map { inline(it) }

	// -- inline content ------------------------------------------------------

	private data class Flags(
		val bold: Boolean = false,
		val italic: Boolean = false,
		val strikethrough: Boolean = false,
		val link: String? = null,
	)

	private fun inline(holder: ASTNode): List<ProseSpan> {
		val raw = mutableListOf<ProseSpan>()
		if (holder.children.isEmpty()) {
			collect(holder, Flags(), raw)
		} else {
			holder.children.forEach { collect(it, Flags(), raw) }
		}
		return normalise(raw)
	}

	private fun collect(node: ASTNode, flags: Flags, out: MutableList<ProseSpan>) {
		when (node.type) {
			MarkdownElementTypes.EMPH ->
				node.children.drop(1).dropLast(1)
					.forEach { collect(it, flags.copy(italic = true), out) }

			MarkdownElementTypes.STRONG ->
				node.children.drop(2).dropLast(2)
					.forEach { collect(it, flags.copy(bold = true), out) }

			GFMElementTypes.STRIKETHROUGH ->
				trimEqualDelimiters(node.children, GFMTokenTypes.TILDE)
					.forEach { collect(it, flags.copy(strikethrough = true), out) }

			MarkdownElementTypes.CODE_SPAN -> {
				val text = node.children
					.drop(1).dropLast(1)
					.joinToString("") { it.getTextInNode(source) }
					.replace('\n', ' ')
					.trim()
				if (text.isNotEmpty()) out += span(text, flags).copy(code = true)
			}

			MarkdownElementTypes.INLINE_LINK -> {
				val url = linkDestination(node)
				linkText(node)?.let { collect(it, flags.copy(link = url ?: flags.link), out) }
			}

			// Reference links can't be resolved without the link map; keep their text.
			MarkdownElementTypes.FULL_REFERENCE_LINK,
			MarkdownElementTypes.SHORT_REFERENCE_LINK,
			-> linkText(node)?.let { collect(it, flags, out) }

			// Images degrade to their alt text.
			MarkdownElementTypes.IMAGE ->
				node.findChildOfType(MarkdownElementTypes.INLINE_LINK)
					?.let { linkText(it) }
					?.let { collect(it, flags, out) }

			MarkdownElementTypes.LINK_TEXT ->
				node.children.drop(1).dropLast(1).forEach { collect(it, flags, out) }

			MarkdownElementTypes.AUTOLINK -> {
				val url = node.getTextInNode(source).toString().removeSurrounding("<", ">")
				out += span(url, flags.copy(link = url))
			}

			GFMTokenTypes.GFM_AUTOLINK -> {
				val url = node.getTextInNode(source).toString()
				out += span(url, flags.copy(link = url))
			}

			MarkdownTokenTypes.HARD_LINE_BREAK -> out += span("\n", flags)

			MarkdownTokenTypes.EOL,
			MarkdownTokenTypes.WHITE_SPACE,
			-> out += span(" ", flags)

			else -> if (node.children.isEmpty()) {
				out += span(unescapeMarkdown(node.getTextInNode(source)), flags)
			} else {
				node.children.forEach { collect(it, flags, out) }
			}
		}
	}

	private fun span(text: String, flags: Flags): ProseSpan = ProseSpan(
		text = text,
		bold = flags.bold,
		italic = flags.italic,
		strikethrough = flags.strikethrough,
		link = flags.link,
	)

	private fun linkText(node: ASTNode): ASTNode? =
		node.findChildOfType(MarkdownElementTypes.LINK_TEXT)

	private fun linkDestination(node: ASTNode): String? =
		node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
			?.getTextInNode(source)?.toString()
			?.removeSurrounding("<", ">")
			?.let { unescapeMarkdown(it) }
			?.takeIf { it.isNotBlank() }

	private fun trimEqualDelimiters(children: List<ASTNode>, delimiter: IElementType): List<ASTNode> {
		var left = 0
		var right = children.lastIndex
		while (left < right && children[left].type == delimiter && children[right].type == delimiter) {
			left++
			right--
		}
		return children.subList(left, right + 1)
	}

	/** Merges adjacent same-style runs, collapses doubled spaces, trims the paragraph edges. */
	private fun normalise(spans: List<ProseSpan>): List<ProseSpan> {
		val merged = mutableListOf<ProseSpan>()
		for (s in spans) {
			val last = merged.lastOrNull()
			if (last != null && !last.code && !s.code && last.copy(text = "") == s.copy(text = "")) {
				merged[merged.lastIndex] = last.copy(text = last.text + s.text)
			} else {
				merged += s
			}
		}
		// A hard break lexes as the trailing spaces plus a separate EOL, so spaces can
		// straddle the newline we emit; strip them rather than render a stray indent.
		val collapsed = merged.map { s ->
			if (s.code) s
			else s.copy(text = s.text.replace(SPACE_AROUND_NEWLINE, "\n").replace(MULTI_SPACE, " "))
		}
		return collapsed
			.mapIndexed { index, s ->
				var text = s.text
				if (index == 0) text = text.trimStart()
				if (index == collapsed.lastIndex) text = text.trimEnd()
				s.copy(text = text)
			}
			.filter { it.text.isNotEmpty() }
	}
}

private val MULTI_SPACE = Regex(" {2,}")
private val SPACE_AROUND_NEWLINE = Regex(" *\n *")
