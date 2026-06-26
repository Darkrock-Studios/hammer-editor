package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import nl.adaptivity.xmlutil.XMLConstants
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.core.KtXmlWriter
import nl.adaptivity.xmlutil.smartStartTag
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream
import okio.BufferedSink
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import no.synth.kmpzip.okio.ZipOutputStream as OkioZipOutputStream

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val PACKAGE_RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CORE_PROPS_NS =
	"http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
private const val DC_NS = "http://purl.org/dc/elements/1.1/"
private const val APP_PROPS_NS =
	"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"

private const val REL_TYPE_DOCUMENT =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
private const val REL_TYPE_CORE_PROPS =
	"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
private const val REL_TYPE_APP_PROPS =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties"
private const val REL_TYPE_STYLES =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
private const val REL_TYPE_NUMBERING =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering"
private const val REL_TYPE_HYPERLINK =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"

private const val DEFAULT_LINK_COLOR = "0563C1"

private const val BULLET_NUM_ID = 1
private const val BULLET_ABSTRACT_ID = 0
private const val DECIMAL_ABSTRACT_ID = 1

/** Relationship ids rId1/rId2 in document.xml.rels are styles and numbering; hyperlinks follow. */
private const val FIRST_HYPERLINK_REL_ID = 3

/**
 * Renders the story as an OOXML WordprocessingML (.docx) package mirroring the EPUB layout:
 * a title page, a Contents page of hyperlinks to chapter bookmarks, then one chapter per
 * top-level node starting on a fresh page. Chapter bodies are rendered from markdown into
 * styled paragraphs and runs. Heading styles pick up the project theme's accent colors.
 */
fun writeStoryAsDocx(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }

	// The body walk collects hyperlink targets and ordered-list instances that
	// document.xml.rels and numbering.xml must declare, so it runs first.
	val ctx = DocxRenderContext()
	val documentXml = buildXmlPart { writeDocument(it, ctx, projectName, strings, effective) }

	val zip = OkioZipOutputStream(sink)
	zip.putEntry("[Content_Types].xml", buildXmlPart(::writeContentTypes))
	zip.putEntry("_rels/.rels", buildXmlPart(::writePackageRels))
	zip.putEntry("docProps/core.xml", buildXmlPart { writeCoreProps(it, projectName, authorName) })
	zip.putEntry("docProps/app.xml", buildXmlPart(::writeAppProps))
	zip.putEntry("word/styles.xml", buildXmlPart { writeStyles(it, projectData.theme) })
	zip.putEntry("word/numbering.xml", buildXmlPart { writeNumbering(it, ctx.orderedListLevels) })
	zip.putEntry(
		"word/_rels/document.xml.rels",
		buildXmlPart { writeDocumentRels(it, ctx.hyperlinkUrls) })
	zip.putEntry("word/document.xml", documentXml)
	zip.finish()
	zip.flush()
}

private fun ZipOutputStream.putEntry(name: String, content: ByteArray) {
	putNextEntry(ZipEntry(name))
	write(content)
	closeEntry()
}

private fun buildXmlPart(block: (XmlWriter) -> Unit): ByteArray {
	val out = StringBuilder()
	val writer = KtXmlWriter(out, isRepairNamespaces = true, xmlDeclMode = XmlDeclMode.Charset)
	writer.startDocument()
	block(writer)
	writer.endDocument()
	writer.close()
	return out.toString().encodeToByteArray()
}

private class DocxRenderContext {
	val hyperlinkUrls = mutableListOf<String>()

	/** One entry per ordered list in the document, holding its indent level; entry index maps to its numId. */
	val orderedListLevels = mutableListOf<Int>()

	fun addHyperlink(url: String): String {
		hyperlinkUrls += url
		return "rId${hyperlinkUrls.size - 1 + FIRST_HYPERLINK_REL_ID}"
	}

	/** Each ordered list gets its own num instance so numbering restarts per list. */
	fun newOrderedList(ilvl: Int): Int {
		orderedListLevels += ilvl
		return orderedListLevels.size - 1 + orderedNumId(0)
	}
}

private fun orderedNumId(instanceIndex: Int): Int = BULLET_NUM_ID + 1 + instanceIndex

private fun XmlWriter.w(name: String, body: XmlWriter.() -> Unit = {}) =
	smartStartTag(W_NS, name, "w", body)

private fun XmlWriter.wAttr(name: String, value: String) =
	attribute(W_NS, name, "w", value)

private fun XmlWriter.wVal(name: String, value: String) =
	w(name) { wAttr("val", value) }

private fun XmlWriter.attr(name: String, value: String) =
	attribute(null, name, null, value)

private fun XmlWriter.preserveSpace() =
	attribute(XMLConstants.XML_NS_URI, "space", "xml", "preserve")

private fun writeContentTypes(writer: XmlWriter) {
	fun XmlWriter.default(extension: String, contentType: String) =
		smartStartTag(CONTENT_TYPES_NS, "Default", "") {
			attr("Extension", extension)
			attr("ContentType", contentType)
		}

	fun XmlWriter.override(partName: String, contentType: String) =
		smartStartTag(CONTENT_TYPES_NS, "Override", "") {
			attr("PartName", partName)
			attr("ContentType", contentType)
		}

	writer.smartStartTag(CONTENT_TYPES_NS, "Types", "") {
		default("rels", "application/vnd.openxmlformats-package.relationships+xml")
		default("xml", "application/xml")
		override(
			"/word/document.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
		)
		override(
			"/word/styles.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"
		)
		override(
			"/word/numbering.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"
		)
		override("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml")
		override(
			"/docProps/app.xml",
			"application/vnd.openxmlformats-officedocument.extended-properties+xml"
		)
	}
}

private fun XmlWriter.relationship(
	id: String,
	type: String,
	target: String,
	external: Boolean = false
) =
	smartStartTag(PACKAGE_RELS_NS, "Relationship", "") {
		attr("Id", id)
		attr("Type", type)
		attr("Target", target)
		if (external) attr("TargetMode", "External")
	}

private fun writePackageRels(writer: XmlWriter) {
	writer.smartStartTag(PACKAGE_RELS_NS, "Relationships", "") {
		relationship("rId1", REL_TYPE_DOCUMENT, "word/document.xml")
		relationship("rId2", REL_TYPE_CORE_PROPS, "docProps/core.xml")
		relationship("rId3", REL_TYPE_APP_PROPS, "docProps/app.xml")
	}
}

private fun writeDocumentRels(writer: XmlWriter, hyperlinkUrls: List<String>) {
	writer.smartStartTag(PACKAGE_RELS_NS, "Relationships", "") {
		relationship("rId1", REL_TYPE_STYLES, "styles.xml")
		relationship("rId2", REL_TYPE_NUMBERING, "numbering.xml")
		hyperlinkUrls.forEachIndexed { index, url ->
			relationship(
				"rId${index + FIRST_HYPERLINK_REL_ID}",
				REL_TYPE_HYPERLINK,
				url,
				external = true
			)
		}
	}
}

private fun writeCoreProps(writer: XmlWriter, projectName: String, authorName: String?) {
	writer.smartStartTag(CORE_PROPS_NS, "coreProperties", "cp") {
		namespaceAttr("dc", DC_NS)
		smartStartTag(DC_NS, "title", "dc") { text(projectName) }
		authorName?.let { smartStartTag(DC_NS, "creator", "dc") { text(it) } }
	}
}

private fun writeAppProps(writer: XmlWriter) {
	writer.smartStartTag(APP_PROPS_NS, "Properties", "") {
		smartStartTag(APP_PROPS_NS, "Application", "") { text("Hammer") }
		smartStartTag(APP_PROPS_NS, "AppVersion", "") { text(BuildMetadata.APP_VERSION) }
	}
}

private fun writeStyles(writer: XmlWriter, theme: ProjectTheme?) {
	val primary = theme?.primary?.let(::argbHexToCssHex)?.removePrefix("#")
	val secondary = theme?.secondary?.let(::argbHexToCssHex)?.removePrefix("#")

	fun XmlWriter.rFonts(font: String) = w("rFonts") {
		wAttr("ascii", font)
		wAttr("hAnsi", font)
		wAttr("cs", font)
	}

	fun XmlWriter.size(halfPoints: Int) {
		w("sz") { wAttr("val", halfPoints.toString()) }
		w("szCs") { wAttr("val", halfPoints.toString()) }
	}

	fun XmlWriter.style(
		styleId: String,
		name: String,
		type: String = "paragraph",
		default: Boolean = false,
		body: XmlWriter.() -> Unit = {},
	) = w("style") {
		wAttr("type", type)
		if (default) wAttr("default", "1")
		wAttr("styleId", styleId)
		wVal("name", name)
		if (styleId != "Normal" && type == "paragraph") wVal("basedOn", "Normal")
		body()
	}

	writer.smartStartTag(W_NS, "styles", "w") {
		w("docDefaults") {
			w("rPrDefault") {
				w("rPr") {
					rFonts(EXPORT_BODY_FONT)
					size(24)
				}
			}
			w("pPrDefault") {
				w("pPr") {
					w("spacing") { wAttr("after", "160") }
				}
			}
		}

		style("Normal", "Normal", default = true)

		style("BodyText", "Body Text") {
			w("pPr") {
				w("ind") { wAttr("firstLine", "360") }
			}
		}

		style("Title", "Title") {
			w("pPr") {
				w("spacing") {
					wAttr("before", "3600")
					wAttr("after", "240")
				}
				wVal("jc", "center")
			}
			w("rPr") {
				w("b")
				primary?.let { wVal("color", it) }
				size(72)
			}
		}

		style("TocTitle", "TOC Title") {
			w("pPr") {
				w("spacing") {
					wAttr("before", "240")
					wAttr("after", "240")
				}
				wVal("jc", "center")
			}
			w("rPr") {
				w("b")
				size(48)
			}
		}

		HEADING_HALF_POINTS.forEachIndexed { index, halfPoints ->
			val level = index + 1
			style("Heading$level", "heading $level") {
				wVal("next", "Normal")
				w("pPr") {
					w("keepNext")
					w("spacing") {
						wAttr("before", "360")
						wAttr("after", "160")
					}
					wVal("outlineLvl", index.toString())
				}
				w("rPr") {
					w("b")
					when (level) {
						1 -> primary?.let { wVal("color", it) }
						2 -> secondary?.let { wVal("color", it) }
					}
					size(halfPoints)
				}
			}
		}

		style("Quote", "Quote") {
			w("pPr") {
				w("ind") { wAttr("left", "720") }
			}
			w("rPr") {
				w("i")
			}
		}

		style("Hyperlink", "Hyperlink", type = "character") {
			w("rPr") {
				wVal("color", primary ?: DEFAULT_LINK_COLOR)
				wVal("u", "single")
			}
		}
	}
}

private fun writeNumbering(writer: XmlWriter, orderedListLevels: List<Int>) {
	val bulletGlyphs = listOf("•", "◦", "▪")

	fun XmlWriter.level(ilvl: Int, numFmt: String, lvlText: String) = w("lvl") {
		wAttr("ilvl", ilvl.toString())
		wVal("start", "1")
		wVal("numFmt", numFmt)
		wVal("lvlText", lvlText)
		wVal("lvlJc", "left")
		w("pPr") {
			w("ind") {
				wAttr("left", (720 * (ilvl + 1)).toString())
				wAttr("hanging", "360")
			}
		}
	}

	writer.smartStartTag(W_NS, "numbering", "w") {
		w("abstractNum") {
			wAttr("abstractNumId", BULLET_ABSTRACT_ID.toString())
			wVal("multiLevelType", "hybridMultilevel")
			for (ilvl in 0..8) level(ilvl, "bullet", bulletGlyphs[ilvl % bulletGlyphs.size])
		}
		w("abstractNum") {
			wAttr("abstractNumId", DECIMAL_ABSTRACT_ID.toString())
			wVal("multiLevelType", "hybridMultilevel")
			for (ilvl in 0..8) level(ilvl, "decimal", "%${ilvl + 1}.")
		}

		w("num") {
			wAttr("numId", BULLET_NUM_ID.toString())
			wVal("abstractNumId", BULLET_ABSTRACT_ID.toString())
		}
		orderedListLevels.forEachIndexed { index, ilvl ->
			w("num") {
				wAttr("numId", orderedNumId(index).toString())
				wVal("abstractNumId", DECIMAL_ABSTRACT_ID.toString())
				w("lvlOverride") {
					wAttr("ilvl", ilvl.toString())
					wVal("startOverride", "1")
				}
			}
		}
	}
}

private fun writeDocument(
	writer: XmlWriter,
	ctx: DocxRenderContext,
	projectName: String,
	strings: ExportStrings,
	chapters: List<StoryChapter>,
) {
	writer.smartStartTag(W_NS, "document", "w") {
		namespaceAttr("r", R_NS)
		w("body") {
			writeTitlePage(projectName, strings.authorByline)
			writeContentsPage(chapters, strings.contentsTitle)
			chapters.forEachIndexed { index, chapter ->
				writeChapter(ctx, index, chapter)
			}
			w("sectPr") {
				w("pgSz") {
					wAttr("w", "12240")
					wAttr("h", "15840")
				}
				w("pgMar") {
					wAttr("top", "1440")
					wAttr("right", "1440")
					wAttr("bottom", "1440")
					wAttr("left", "1440")
					wAttr("header", "720")
					wAttr("footer", "720")
					wAttr("gutter", "0")
				}
			}
		}
	}
}

private fun XmlWriter.writeTitlePage(projectName: String, authorByline: String?) {
	w("p") {
		w("pPr") { wVal("pStyle", "Title") }
		w("r") {
			w("t") {
				preserveSpace()
				text(projectName)
			}
		}
	}
	authorByline?.let {
		w("p") {
			w("pPr") { wVal("jc", "center") }
			w("r") {
				w("rPr") {
					w("i")
					w("sz") { wAttr("val", "32") }
				}
				w("t") {
					preserveSpace()
					text(it)
				}
			}
		}
	}
}

private fun XmlWriter.writeContentsPage(chapters: List<StoryChapter>, contentsTitle: String) {
	w("p") {
		w("pPr") {
			wVal("pStyle", "TocTitle")
			wVal("pageBreakBefore", "true")
		}
		w("r") {
			w("t") {
				preserveSpace()
				text(contentsTitle)
			}
		}
	}
	chapters.forEachIndexed { index, chapter ->
		w("p") {
			w("hyperlink") {
				wAttr("anchor", chapterBookmark(index))
				w("r") {
					w("rPr") { wVal("rStyle", "Hyperlink") }
					w("t") {
						preserveSpace()
						text("${index + 1}. ${chapter.name}")
					}
				}
			}
		}
	}
}

private fun XmlWriter.writeChapter(ctx: DocxRenderContext, index: Int, chapter: StoryChapter) {
	w("p") {
		w("pPr") {
			wVal("pStyle", "Heading1")
			wVal("pageBreakBefore", "true")
		}
		w("bookmarkStart") {
			wAttr("id", index.toString())
			wAttr("name", chapterBookmark(index))
		}
		w("r") {
			w("t") {
				preserveSpace()
				text("${index + 1}. ${chapter.name}")
			}
		}
		w("bookmarkEnd") { wAttr("id", index.toString()) }
	}
	if (chapter.markdown.isNotBlank()) {
		MarkdownDocxWriter(this, ctx, chapter.markdown).render()
	}
}

/**
 * Walks the markdown AST and streams WordprocessingML paragraphs and runs. Inline formatting
 * is carried as nesting state (bold/italic depth, hyperlink scope) so each emitted run's
 * properties reflect the state at its text's position; literal text accumulates in a buffer
 * that flushes as a single run whenever the state changes.
 */
private class MarkdownDocxWriter(
	private val writer: XmlWriter,
	private val ctx: DocxRenderContext,
	private val source: String,
) {
	private var boldDepth = 0
	private var italicDepth = 0
	private var inHyperlink = false
	private var listDepth = -1
	private val textBuffer = StringBuilder()

	private data class NumPr(val numId: Int, val ilvl: Int)
	private data class BlockContext(val style: String? = null, val numbering: NumPr? = null)

	fun render() {
		val tree = MarkdownParser(CommonMarkFlavourDescriptor()).buildMarkdownTreeFromString(source)
		renderBlocks(tree.children, BlockContext())
	}

	private fun renderBlocks(nodes: List<ASTNode>, blockCtx: BlockContext) {
		// Only the first paragraph of a list item carries the number; trailing blocks flow under it.
		var pendingNumbering = blockCtx.numbering
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.PARAGRAPH -> {
					paragraph(blockCtx.style, pendingNumbering) { renderInline(node.children) }
					pendingNumbering = null
				}

				MarkdownElementTypes.ATX_1 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 1)
				MarkdownElementTypes.ATX_2 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 2)
				MarkdownElementTypes.ATX_3 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 3)
				MarkdownElementTypes.ATX_4 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 4)
				MarkdownElementTypes.ATX_5 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 5)
				MarkdownElementTypes.ATX_6 -> heading(node, MarkdownTokenTypes.ATX_CONTENT, 6)
				MarkdownElementTypes.SETEXT_1 -> heading(node, MarkdownTokenTypes.SETEXT_CONTENT, 1)
				MarkdownElementTypes.SETEXT_2 -> heading(node, MarkdownTokenTypes.SETEXT_CONTENT, 2)

				MarkdownElementTypes.BLOCK_QUOTE -> renderBlocks(
					node.children,
					blockCtx.copy(style = "Quote")
				)

				MarkdownElementTypes.UNORDERED_LIST -> renderList(node, ordered = false, blockCtx)
				MarkdownElementTypes.ORDERED_LIST -> renderList(node, ordered = true, blockCtx)

				MarkdownElementTypes.CODE_FENCE -> codeFence(node)
				MarkdownElementTypes.CODE_BLOCK -> codeBlock(node)
				MarkdownElementTypes.LINK_DEFINITION -> Unit

				MarkdownTokenTypes.HORIZONTAL_RULE -> horizontalRule()

				MarkdownTokenTypes.EOL,
				MarkdownTokenTypes.WHITE_SPACE,
				MarkdownTokenTypes.BLOCK_QUOTE,
				MarkdownTokenTypes.LIST_BULLET,
				MarkdownTokenTypes.LIST_NUMBER,
					-> Unit

				else -> {
					if (node.children.isNotEmpty()) {
						renderBlocks(node.children, blockCtx)
					} else {
						val literal = node.getTextInNode(source).toString()
						if (literal.isNotBlank()) {
							paragraph(blockCtx.style, pendingNumbering) { appendText(literal) }
							pendingNumbering = null
						}
					}
				}
			}
		}
	}

	private fun renderList(node: ASTNode, ordered: Boolean, blockCtx: BlockContext) {
		listDepth++
		val numId = if (ordered) ctx.newOrderedList(listDepth) else BULLET_NUM_ID
		node.children
			.filter { it.type == MarkdownElementTypes.LIST_ITEM }
			.forEach { item ->
				renderBlocks(item.children, blockCtx.copy(numbering = NumPr(numId, listDepth)))
			}
		listDepth--
	}

	private fun heading(node: ASTNode, contentType: Any, level: Int) {
		val content = node.children.firstOrNull { it.type == contentType }
		paragraph(style = "Heading$level", numPr = null) {
			val children = content?.children.orEmpty()
				.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
				.dropLastWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }
			if (children.isNotEmpty()) {
				renderInline(children)
			} else {
				content?.let { appendText(unescapeMarkdown(it.getTextInNode(source)).trim()) }
			}
		}
	}

	private fun renderInline(nodes: List<ASTNode>) {
		for (node in nodes) {
			when (node.type) {
				MarkdownElementTypes.STRONG -> formatted(bold = true) {
					renderInline(node.children.stripDelimiters(MarkdownTokenTypes.EMPH))
				}

				MarkdownElementTypes.EMPH -> formatted(italic = true) {
					renderInline(node.children.stripDelimiters(MarkdownTokenTypes.EMPH))
				}

				MarkdownElementTypes.CODE_SPAN -> {
					flushRun()
					val code = node.children
						.filter { it.type != MarkdownTokenTypes.BACKTICK }
						.joinToString("") { it.getTextInNode(source) }
					emitRun(code.removeSurrounding(" "), code = true)
				}

				MarkdownElementTypes.INLINE_LINK -> inlineLink(node)
				MarkdownElementTypes.AUTOLINK -> autoLink(node)

				MarkdownTokenTypes.HARD_LINE_BREAK -> {
					flushRun()
					writer.w("r") { w("br") }
				}

				MarkdownTokenTypes.EOL -> appendText(" ")

				else -> {
					if (node.children.isEmpty()) {
						appendText(unescapeMarkdown(node.getTextInNode(source)))
					} else {
						renderInline(node.children)
					}
				}
			}
		}
	}

	private fun formatted(bold: Boolean = false, italic: Boolean = false, body: () -> Unit) {
		flushRun()
		if (bold) boldDepth++
		if (italic) italicDepth++
		body()
		flushRun()
		if (bold) boldDepth--
		if (italic) italicDepth--
	}

	private fun inlineLink(node: ASTNode) {
		val destination =
			node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
				?.getTextInNode(source)?.toString()?.removeSurrounding("<", ">")
		val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
		if (destination == null || linkText == null) {
			appendText(node.getTextInNode(source))
			return
		}
		hyperlink(destination) {
			renderInline(
				linkText.children.filter {
					it.type != MarkdownTokenTypes.LBRACKET && it.type != MarkdownTokenTypes.RBRACKET
				}
			)
		}
	}

	private fun autoLink(node: ASTNode) {
		val url = node.getTextInNode(source).toString().removeSurrounding("<", ">")
		hyperlink(url) { appendText(url) }
	}

	private fun hyperlink(url: String, body: () -> Unit) {
		flushRun()
		val relId = ctx.addHyperlink(url)
		writer.w("hyperlink") {
			attribute(R_NS, "id", "r", relId)
			inHyperlink = true
			body()
			flushRun()
			inHyperlink = false
		}
	}

	private fun codeFence(node: ASTNode) {
		val lines = collectCodeLines(node, source, MarkdownTokenTypes.CODE_FENCE_CONTENT)
		lines.forEach { codeParagraph(it) }
	}

	private fun codeBlock(node: ASTNode) {
		val lines = collectCodeLines(node, source, MarkdownTokenTypes.CODE_LINE)
			.map { it.removePrefix("    ").removePrefix("\t") }
		lines.forEach { codeParagraph(it) }
	}

	private fun codeParagraph(line: String) {
		writer.w("p") {
			this@MarkdownDocxWriter.emitRun(line, code = true)
		}
	}

	private fun horizontalRule() {
		writer.w("p") {
			w("pPr") {
				w("pBdr") {
					w("bottom") {
						wAttr("val", "single")
						wAttr("sz", "6")
						wAttr("space", "1")
						wAttr("color", "auto")
					}
				}
			}
		}
	}

	private fun paragraph(style: String?, numPr: NumPr?, body: () -> Unit) {
		// Plain prose gets the first-line-indented body style; numbered paragraphs control their own indent.
		val effectiveStyle = style ?: if (numPr == null) "BodyText" else null
		writer.w("p") {
			if (effectiveStyle != null || numPr != null) {
				w("pPr") {
					effectiveStyle?.let { wVal("pStyle", it) }
					numPr?.let {
						w("numPr") {
							wVal("ilvl", it.ilvl.toString())
							wVal("numId", it.numId.toString())
						}
					}
				}
			}
			body()
			this@MarkdownDocxWriter.flushRun()
		}
	}

	private fun appendText(text: CharSequence) {
		textBuffer.append(text)
	}

	private fun flushRun() {
		if (textBuffer.isEmpty()) return
		val text = textBuffer.toString()
		textBuffer.clear()
		emitRun(text)
	}

	private fun emitRun(text: String, code: Boolean = false) {
		writer.w("r") {
			val needsProps = code || inHyperlink || boldDepth > 0 || italicDepth > 0
			if (needsProps) {
				w("rPr") {
					if (inHyperlink) wVal("rStyle", "Hyperlink")
					if (code) {
						w("rFonts") {
							wAttr("ascii", EXPORT_MONO_FONT)
							wAttr("hAnsi", EXPORT_MONO_FONT)
							wAttr("cs", EXPORT_MONO_FONT)
						}
					}
					if (boldDepth > 0) w("b")
					if (italicDepth > 0) w("i")
				}
			}
			w("t") {
				preserveSpace()
				text(text)
			}
		}
	}

}
