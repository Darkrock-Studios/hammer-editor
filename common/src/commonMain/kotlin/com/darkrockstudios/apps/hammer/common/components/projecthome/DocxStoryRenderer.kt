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
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
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
		MarkdownDocxWriter(this, ctx).render(chapter.markdown)
	}
}

/**
 * Walks the markdown AST and streams WordprocessingML paragraphs and runs. Inline formatting
 * is carried as nesting state (bold/italic depth, hyperlink scope) so each emitted run's
 * properties reflect the state at its text's position; literal text accumulates in a buffer
 * that flushes as a single run whenever the state changes.
 */
/**
 * Renders a chapter's markdown into WordprocessingML by consuming the shared [parseProseMarkdown]
 * model (CommonMark flavour) and streaming `<w:p>` / `<w:r>` into [writer]. Block kinds map onto
 * paragraph styles; inline spans become runs, with consecutive same-link spans wrapped in one
 * `<w:hyperlink>` (its URL registered with [ctx] in document order) and `\n` hard breaks split into
 * `<w:br/>`.
 */
private class MarkdownDocxWriter(
	private val writer: XmlWriter,
	private val ctx: DocxRenderContext,
) {
	fun render(markdown: String) {
		for (block in parseProseMarkdown(markdown, CommonMarkFlavourDescriptor())) {
			when (block) {
				is ProseBlock.Paragraph -> paragraph(style = null, numId = null) { runs(block.spans) }
				is ProseBlock.Heading -> paragraph(style = "Heading${block.level}", numId = null) { runs(block.spans) }
				is ProseBlock.Listing -> {
					val numbering = DocxListNumbering(ctx)
					block.items.forEach { item ->
						val (numId, ilvl) = numbering.numbering(item)
						paragraph(style = null, numId = numId, ilvl = ilvl) { runs(item.spans) }
					}
				}

				is ProseBlock.Quote -> block.paragraphs.forEach { paragraph(style = "Quote", numId = null) { runs(it) } }
				is ProseBlock.CodeBlock -> codeBlockLines(block.code).forEach { codeParagraph(it) }
				ProseBlock.Rule -> horizontalRule()
				is ProseBlock.Table -> tableFallback(block)
			}
		}
	}

	/** Emits runs for [spans], wrapping consecutive same-link spans in one `<w:hyperlink>`. */
	private fun runs(spans: List<ProseSpan>) {
		var i = 0
		while (i < spans.size) {
			val link = spans[i].link
			if (link != null) {
				val relId = ctx.addHyperlink(link)
				writer.w("hyperlink") {
					attribute(R_NS, "id", "r", relId)
					while (i < spans.size && spans[i].link == link) {
						emitSpan(spans[i], hyperlink = true)
						i++
					}
				}
			} else {
				emitSpan(spans[i], hyperlink = false)
				i++
			}
		}
	}

	private fun emitSpan(span: ProseSpan, hyperlink: Boolean) {
		// A hard break rides in span text as '\n'; emit a <w:br/> between the surrounding fragments.
		span.text.split("\n").forEachIndexed { index, part ->
			if (index > 0) writer.w("r") { w("br") }
			if (part.isNotEmpty()) emitRun(part, span, hyperlink)
		}
	}

	private fun emitRun(text: String, span: ProseSpan, hyperlink: Boolean) {
		writer.w("r") {
			val needsProps = span.code || hyperlink || span.bold || span.italic || span.strikethrough
			if (needsProps) {
				w("rPr") {
					if (hyperlink) wVal("rStyle", "Hyperlink")
					if (span.code) {
						w("rFonts") {
							wAttr("ascii", EXPORT_MONO_FONT)
							wAttr("hAnsi", EXPORT_MONO_FONT)
							wAttr("cs", EXPORT_MONO_FONT)
						}
					}
					if (span.bold) w("b")
					if (span.italic) w("i")
					if (span.strikethrough) w("strike")
				}
			}
			w("t") {
				preserveSpace()
				text(text)
			}
		}
	}

	private fun codeParagraph(line: String) {
		writer.w("p") {
			this@MarkdownDocxWriter.emitRun(line, ProseSpan(text = line, code = true), hyperlink = false)
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

	/** Tab-separated text fallback for tables (never produced under CommonMark; defensive). */
	private fun tableFallback(block: ProseBlock.Table) {
		(listOf(block.header) + block.rows).forEach { row ->
			paragraph(style = null, numId = null) {
				row.forEachIndexed { index, cell ->
					if (index > 0) writer.w("r") { w("t") { text("\t") } }
					runs(cell)
				}
			}
		}
	}

	private fun paragraph(style: String?, numId: Int?, ilvl: Int = 0, body: () -> Unit) {
		// Plain prose gets the first-line-indented body style; numbered paragraphs control their own indent.
		val effectiveStyle = style ?: if (numId == null) "BodyText" else null
		writer.w("p") {
			if (effectiveStyle != null || numId != null) {
				w("pPr") {
					effectiveStyle?.let { wVal("pStyle", it) }
					if (numId != null) {
						w("numPr") {
							wVal("ilvl", ilvl.toString())
							wVal("numId", numId.toString())
						}
					}
				}
			}
			body()
		}
	}
}

/**
 * Allocates Word numbering for a single [ProseBlock.Listing]. Bullet items share [BULLET_NUM_ID];
 * each ordered sublist gets its own `numId` (via [DocxRenderContext.newOrderedList]) so its numbering
 * restarts. Items arrive flattened in reading order, so descending into a level allocates a fresh
 * ordered instance and ascending drops the deeper ones.
 */
private class DocxListNumbering(private val ctx: DocxRenderContext) {
	private val orderedNumIdByLevel = mutableMapOf<Int, Int>()
	private var lastLevel = -1

	/** Returns the `(numId, ilvl)` for [item]. */
	fun numbering(item: ProseListItem): Pair<Int, Int> {
		val level = item.level
		if (level < lastLevel) {
			orderedNumIdByLevel.keys.filter { it > level }.toList().forEach(orderedNumIdByLevel::remove)
		}
		val numId = if (item.ordered) {
			orderedNumIdByLevel.getOrPut(level) { ctx.newOrderedList(level) }
		} else {
			BULLET_NUM_ID
		}
		lastLevel = level
		return numId to level
	}
}
