package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.util.zip.InMemoryZipEntry
import com.darkrockstudios.apps.hammer.common.util.zip.writeZip
import nl.adaptivity.xmlutil.XMLConstants
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.core.KtXmlWriter
import nl.adaptivity.xmlutil.smartStartTag
import okio.BufferedSink
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val PACKAGE_RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CORE_PROPS_NS =
	"http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
private const val DC_NS = "http://purl.org/dc/elements/1.1/"
private const val DCTERMS_NS = "http://purl.org/dc/terms/"
private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
private const val APP_PROPS_NS =
	"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
private const val WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
private const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"

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
private const val REL_TYPE_SETTINGS =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings"
private const val REL_TYPE_FONT_TABLE =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable"
private const val REL_TYPE_HYPERLINK =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
private const val REL_TYPE_IMAGE =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"

private const val DEFAULT_LINK_COLOR = "0563C1"

private const val BULLET_NUM_ID = 1
private const val BULLET_ABSTRACT_ID = 0
private const val DECIMAL_ABSTRACT_ID = 1

/**
 * Relationship ids rId1 to rId4 in document.xml.rels are styles, numbering, settings and the font
 * table; hyperlinks and images follow.
 */
private const val FIRST_BODY_REL_ID = 5

/** The declaration Word writes at the top of every part. */
private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

/** US Letter with one-inch margins, in twips. */
private const val PAGE_WIDTH_TWIPS = 12240
private const val PAGE_HEIGHT_TWIPS = 15840
private const val PAGE_MARGIN_TWIPS = 1440

private const val EMU_PER_TWIP = 635L

/** The document default space after a paragraph, in twips. */
private const val PARAGRAPH_SPACE = "160"

/**
 * Renders the story as an OOXML WordprocessingML (.docx) package mirroring the EPUB layout:
 * a cover page when [images] holds a project cover [exportRaster] can read, a title page, a
 * Contents page of hyperlinks to chapter bookmarks, then one chapter per top-level node starting
 * on a fresh page. Chapter bodies are rendered from markdown into styled paragraphs and runs.
 * Heading styles pick up the project theme's accent colors.
 *
 * A project image on a line of its own is embedded from [images], centred at 70% of the text width
 * and no taller than 60% of the text height. One missing there, an SVG, or one [exportRaster] cannot
 * read is written as its name.
 *
 * [clock] dates the package's creation and modification in its core properties.
 */
fun writeStoryAsDocx(
	sink: BufferedSink,
	projectName: String,
	projectData: ProjectData,
	chapters: List<StoryChapter>,
	strings: ExportStrings,
	options: ExportOptions = ExportOptions(),
	imageNames: Map<Int, String> = emptyMap(),
	images: Map<Int, ExportImage> = emptyMap(),
	clock: Clock = Clock.System,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val effective = chapters.ifEmpty { listOf(StoryChapter(projectName, "")) }

	// The body walk collects hyperlink targets and ordered-list instances that
	// document.xml.rels and numbering.xml must declare, so it runs first.
	val ctx = DocxRenderContext(imageNames, images)
	val documentXml = buildXmlPart { writeDocument(it, ctx, projectName, projectData, strings, effective, options) }

	val mediaFormats = ctx.media.map { it.raster.format }.toSet()
	// Word dates core properties to the second.
	val now = Instant.fromEpochSeconds(clock.now().epochSeconds)
	writeZip(
		sink,
		listOf(
			InMemoryZipEntry("[Content_Types].xml", buildXmlPart { writeContentTypes(it, mediaFormats) }),
			InMemoryZipEntry("_rels/.rels", buildXmlPart(::writePackageRels)),
			InMemoryZipEntry("docProps/core.xml", buildXmlPart { writeCoreProps(it, projectName, authorName, now) }),
			InMemoryZipEntry("docProps/app.xml", buildXmlPart(::writeAppProps)),
			InMemoryZipEntry(
				"word/styles.xml",
				buildXmlPart { writeStyles(it, projectData.theme, options.font.faceName) },
			),
			InMemoryZipEntry("word/numbering.xml", buildXmlPart { writeNumbering(it, ctx.orderedListLevels) }),
			InMemoryZipEntry("word/settings.xml", buildXmlPart(::writeSettings)),
			InMemoryZipEntry("word/fontTable.xml", buildXmlPart { writeFontTable(it, options.font) }),
			InMemoryZipEntry(
				"word/_rels/document.xml.rels",
				buildXmlPart { writeDocumentRels(it, ctx.relationships) },
			),
			InMemoryZipEntry("word/document.xml", documentXml),
		) + ctx.media.map { InMemoryZipEntry("word/${it.target}", it.raster.bytes) },
		stored = true,
	)
}

private fun buildXmlPart(block: (XmlWriter) -> Unit): ByteArray {
	// KtXmlWriter only quotes its declaration with apostrophes, so Word's form is written here.
	val out = StringBuilder(XML_DECLARATION)
	val writer = KtXmlWriter(out, isRepairNamespaces = true, xmlDeclMode = XmlDeclMode.None)
	writer.addTrailingSpaceBeforeEnd = false
	block(writer)
	writer.endDocument()
	writer.close()
	return out.toString().encodeToByteArray()
}

/** A relationship of document.xml; its id is `rId` and its index plus [FIRST_BODY_REL_ID]. */
private class DocxRelationship(val type: String, val target: String, val external: Boolean)

/** An image part, at [target] relative to `word/`, and the id of the relationship that points at it. */
private class DocxMedia(val target: String, val relId: String, val raster: ExportRaster)

private class DocxRenderContext(val imageNames: Map<Int, String>, val images: Map<Int, ExportImage>) {
	/** Hyperlinks and images in the order the body first refers to them. */
	val relationships = mutableListOf<DocxRelationship>()

	val media = mutableListOf<DocxMedia>()

	/** Null for an image that is written as its name. */
	private val mediaByImageId = mutableMapOf<Int, DocxMedia?>()

	private var drawingCount = 0

	/** One entry per ordered list in the document, holding its indent level; entry index maps to its numId. */
	val orderedListLevels = mutableListOf<Int>()

	fun addHyperlink(url: String): String = addRelationship(DocxRelationship(REL_TYPE_HYPERLINK, url, external = true))

	/** The part for [imageId], added the first time the body shows it, or null when it has none. */
	fun mediaFor(imageId: Int): DocxMedia? {
		if (imageId !in mediaByImageId) {
			mediaByImageId[imageId] = images[imageId]?.let(::exportRaster)?.let { raster ->
				val target = "media/image${media.size + 1}.${raster.format.extension}"
				val relId = addRelationship(DocxRelationship(REL_TYPE_IMAGE, target, external = false))
				DocxMedia(target, relId, raster).also { media += it }
			}
		}
		return mediaByImageId[imageId]
	}

	/** Unique per `wp:docPr`, as Word requires. */
	fun nextDrawingId(): Int = ++drawingCount

	private fun addRelationship(relationship: DocxRelationship): String {
		relationships += relationship
		return "rId${relationships.size - 1 + FIRST_BODY_REL_ID}"
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

private fun writeContentTypes(writer: XmlWriter, mediaFormats: Set<ExportRaster.Format>) {
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
		ExportRaster.Format.entries.filter { it in mediaFormats }.forEach { default(it.extension, it.mimeType) }
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
		override(
			"/word/settings.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"
		)
		override(
			"/word/fontTable.xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"
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

private fun writeDocumentRels(writer: XmlWriter, relationships: List<DocxRelationship>) {
	writer.smartStartTag(PACKAGE_RELS_NS, "Relationships", "") {
		relationship("rId1", REL_TYPE_STYLES, "styles.xml")
		relationship("rId2", REL_TYPE_NUMBERING, "numbering.xml")
		relationship("rId3", REL_TYPE_SETTINGS, "settings.xml")
		relationship("rId4", REL_TYPE_FONT_TABLE, "fontTable.xml")
		relationships.forEachIndexed { index, it ->
			relationship("rId${index + FIRST_BODY_REL_ID}", it.type, it.target, it.external)
		}
	}
}

private fun writeCoreProps(writer: XmlWriter, projectName: String, authorName: String?, now: Instant) {
	fun XmlWriter.date(name: String) = smartStartTag(DCTERMS_NS, name, "dcterms") {
		// The type is a QName, so it relies on the dcterms prefix declared on the root.
		attribute(XSI_NS, "type", "xsi", "dcterms:W3CDTF")
		text(now.toString())
	}

	writer.smartStartTag(CORE_PROPS_NS, "coreProperties", "cp") {
		namespaceAttr("dc", DC_NS)
		namespaceAttr("dcterms", DCTERMS_NS)
		namespaceAttr("xsi", XSI_NS)
		smartStartTag(DC_NS, "title", "dc") { text(projectName) }
		authorName?.let { smartStartTag(DC_NS, "creator", "dc") { text(it) } }
		date("created")
		date("modified")
	}
}

private fun writeAppProps(writer: XmlWriter) {
	writer.smartStartTag(APP_PROPS_NS, "Properties", "") {
		smartStartTag(APP_PROPS_NS, "Application", "") { text("Hammer") }
		smartStartTag(APP_PROPS_NS, "AppVersion", "") { text(BuildMetadata.APP_VERSION) }
	}
}

/** Compatibility mode 15 has Word lay the document out as Word 2013 and later do, not in compatibility mode. */
private fun writeSettings(writer: XmlWriter) {
	writer.smartStartTag(W_NS, "settings", "w") {
		w("zoom") { wAttr("percent", "100") }
		wVal("defaultTabStop", "720")
		wVal("characterSpacingControl", "doNotCompress")
		w("compat") {
			w("compatSetting") {
				wAttr("name", "compatibilityMode")
				wAttr("uri", "http://schemas.microsoft.com/office/word")
				wAttr("val", "15")
			}
		}
	}
}

/** Declares [bodyFont] and the code font, with the generic family a reader substitutes from when one is missing. */
private fun writeFontTable(writer: XmlWriter, bodyFont: ExportFont) {
	val bodyFamily = when (bodyFont) {
		ExportFont.Georgia, ExportFont.TimesNewRoman, ExportFont.Garamond -> "roman"
		ExportFont.Arial, ExportFont.Verdana -> "swiss"
		ExportFont.CourierNew -> "modern"
	}
	val fonts = listOf(bodyFont.faceName to bodyFamily, EXPORT_MONO_FONT to "modern").distinctBy { it.first }

	writer.smartStartTag(W_NS, "fonts", "w") {
		fonts.forEach { (name, family) ->
			w("font") {
				wAttr("name", name)
				wVal("charset", "00")
				wVal("family", family)
				wVal("pitch", if (family == "modern") "fixed" else "variable")
			}
		}
	}
}

private fun writeStyles(writer: XmlWriter, theme: ProjectTheme?, bodyFont: String) {
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
					rFonts(bodyFont)
					size(24)
				}
			}
			w("pPrDefault") {
				w("pPr") {
					w("spacing") { wAttr("after", PARAGRAPH_SPACE) }
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
	projectData: ProjectData,
	strings: ExportStrings,
	chapters: List<StoryChapter>,
	options: ExportOptions,
) {
	writer.smartStartTag(W_NS, "document", "w") {
		namespaceAttr("r", R_NS)
		namespaceAttr("wp", WP_NS)
		namespaceAttr("a", A_NS)
		namespaceAttr("pic", PIC_NS)
		w("body") {
			val cover = exportCover(projectData, ctx.images) { ctx.mediaFor(it.id)?.let { media -> it.name to media } }
			cover?.let { (name, media) -> writeCoverPage(ctx, name, media) }
			writeTitlePage(projectName, strings.authorByline, pageBreakBefore = cover != null)
			writeContentsPage(chapters, strings.contentsTitle, options.numberChapters)
			chapters.forEachIndexed { index, chapter ->
				writeChapter(ctx, index, chapter, options)
			}
			w("sectPr") {
				w("pgSz") {
					wAttr("w", PAGE_WIDTH_TWIPS.toString())
					wAttr("h", PAGE_HEIGHT_TWIPS.toString())
				}
				w("pgMar") {
					wAttr("top", PAGE_MARGIN_TWIPS.toString())
					wAttr("right", PAGE_MARGIN_TWIPS.toString())
					wAttr("bottom", PAGE_MARGIN_TWIPS.toString())
					wAttr("left", PAGE_MARGIN_TWIPS.toString())
					wAttr("header", "720")
					wAttr("footer", "720")
					wAttr("gutter", "0")
				}
			}
		}
	}
}

/** [media] filling the text width of the first page, or as much of it as keeps within the text height. */
private fun XmlWriter.writeCoverPage(ctx: DocxRenderContext, name: String, media: DocxMedia) {
	val (cx, cy) = imageExtent(media.raster.aspectRatio, widthFraction = 1f, maxHeightFraction = 1f)
	picture(name, media, ctx.nextDrawingId(), cx, cy, spaceBefore = "0", spaceAfter = "0")
}

private fun XmlWriter.writeTitlePage(projectName: String, authorByline: String?, pageBreakBefore: Boolean) {
	w("p") {
		w("pPr") {
			wVal("pStyle", "Title")
			if (pageBreakBefore) wVal("pageBreakBefore", "true")
		}
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

private fun XmlWriter.writeContentsPage(
	chapters: List<StoryChapter>,
	contentsTitle: String,
	numberChapters: Boolean,
) {
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
						text(chapterTitle(index, chapter, numberChapters))
					}
				}
			}
		}
	}
}

private fun XmlWriter.writeChapter(
	ctx: DocxRenderContext,
	index: Int,
	chapter: StoryChapter,
	options: ExportOptions,
) {
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
				text(chapterTitle(index, chapter, options.numberChapters))
			}
		}
		w("bookmarkEnd") { wAttr("id", index.toString()) }
	}
	if (chapter.markdown.isNotBlank()) {
		MarkdownDocxWriter(this, ctx, options.keepBlankLines).render(chapter.markdown)
	}
}

/**
 * Renders a chapter's markdown into WordprocessingML by consuming the shared [parseProseMarkdown]
 * model and streaming `<w:p>` / `<w:r>` into [writer]. Block kinds map onto paragraph styles; inline
 * spans become runs, with consecutive same-link spans wrapped in one `<w:hyperlink>` (its URL
 * registered with [ctx] in document order) and `\n` hard breaks split into `<w:br/>`.
 */
private class MarkdownDocxWriter(
	private val writer: XmlWriter,
	private val ctx: DocxRenderContext,
	private val keepBlankLines: Boolean,
) {
	fun render(markdown: String) {
		val blocks = parseProseMarkdown(markdown, keepBlankLines, ctx.imageNames::get)
		blocks.forEachIndexed { index, block ->
			// Prose runs tight: the indent parts one line from the next, and the space a passage
			// break wants is a blank line the author typed. Space after is for leaving prose behind.
			val runsOn = blocks.getOrNull(index + 1)?.isProseLine == true
			when (block) {
				is ProseBlock.Paragraph ->
					paragraph(style = null, numId = null, tight = runsOn) { runs(block.spans) }

				is ProseBlock.Image -> {
					val media = ctx.mediaFor(block.imageId)
					if (media != null) {
						picture(
							block.name,
							media,
							previous = blocks.getOrNull(index - 1),
							next = blocks.getOrNull(index + 1),
						)
					} else {
						paragraph(style = null, numId = null, tight = runsOn) { runs(block.spans) }
					}
				}

				ProseBlock.Blank -> paragraph(style = null, numId = null, tight = runsOn) {}
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

	/** Tab-separated text fallback for GFM pipe tables, which the export has no real table layout for yet. */
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

	/**
	 * [media] as a [picture] paragraph a paragraph gap from its neighbours, unless a blank line already
	 * parts them. Prose beside an image runs on into it and leaves no gap of its own, so the picture
	 * supplies both.
	 */
	private fun picture(name: String, media: DocxMedia, previous: ProseBlock?, next: ProseBlock?) {
		val (cx, cy) = imageExtent(media.raster.aspectRatio, IMAGE_WIDTH_FRACTION, MAX_IMAGE_HEIGHT_FRACTION)
		writer.picture(
			name = name,
			media = media,
			drawingId = ctx.nextDrawingId(),
			cx = cx,
			cy = cy,
			spaceBefore = if (previous == ProseBlock.Blank) "0" else PARAGRAPH_SPACE,
			spaceAfter = if (next == ProseBlock.Blank) "0" else PARAGRAPH_SPACE,
		)
	}

	private fun paragraph(
		style: String?,
		numId: Int?,
		ilvl: Int = 0,
		tight: Boolean = false,
		body: () -> Unit,
	) {
		// Plain prose gets the first-line-indented body style; numbered paragraphs control their own indent.
		val effectiveStyle = style ?: if (numId == null) "BodyText" else null
		writer.w("p") {
			if (effectiveStyle != null || numId != null || tight) {
				w("pPr") {
					effectiveStyle?.let { wVal("pStyle", it) }
					if (numId != null) {
						w("numPr") {
							wVal("ilvl", ilvl.toString())
							wVal("numId", numId.toString())
						}
					}
					// Overrides the document default, which would otherwise gap every line of prose.
					if (tight) w("spacing") { wAttr("after", "0") }
				}
			}
			body()
		}
	}
}

/** A centred paragraph holding [media] inline at [cx] by [cy] EMUs, described by [name]. */
private fun XmlWriter.picture(
	name: String,
	media: DocxMedia,
	drawingId: Int,
	cx: Long,
	cy: Long,
	spaceBefore: String,
	spaceAfter: String,
) {
	w("p") {
		w("pPr") {
			w("spacing") {
				wAttr("before", spaceBefore)
				wAttr("after", spaceAfter)
			}
			wVal("jc", "center")
		}
		w("r") {
			w("drawing") {
				smartStartTag(WP_NS, "inline", "wp") {
					for (side in listOf("distT", "distB", "distL", "distR")) attr(side, "0")
					smartStartTag(WP_NS, "extent", "wp") {
						attr("cx", cx.toString())
						attr("cy", cy.toString())
					}
					smartStartTag(WP_NS, "docPr", "wp") {
						attr("id", drawingId.toString())
						attr("name", name)
						attr("descr", name)
					}
					smartStartTag(WP_NS, "cNvGraphicFramePr", "wp") {
						smartStartTag(A_NS, "graphicFrameLocks", "a") { attr("noChangeAspect", "1") }
					}
					smartStartTag(A_NS, "graphic", "a") {
						smartStartTag(A_NS, "graphicData", "a") {
							attr("uri", PIC_NS)
							pic(name, media.relId, cx, cy)
						}
					}
				}
			}
		}
	}
}

private fun XmlWriter.pic(name: String, relId: String, cx: Long, cy: Long) =
	smartStartTag(PIC_NS, "pic", "pic") {
		smartStartTag(PIC_NS, "nvPicPr", "pic") {
			smartStartTag(PIC_NS, "cNvPr", "pic") {
				attr("id", "0")
				attr("name", name)
			}
			smartStartTag(PIC_NS, "cNvPicPr", "pic") {
				smartStartTag(A_NS, "picLocks", "a") { attr("noChangeAspect", "1") }
			}
		}
		smartStartTag(PIC_NS, "blipFill", "pic") {
			smartStartTag(A_NS, "blip", "a") { attribute(R_NS, "embed", "r", relId) }
			smartStartTag(A_NS, "stretch", "a") { smartStartTag(A_NS, "fillRect", "a") {} }
		}
		smartStartTag(PIC_NS, "spPr", "pic") {
			smartStartTag(A_NS, "xfrm", "a") {
				smartStartTag(A_NS, "off", "a") {
					attr("x", "0")
					attr("y", "0")
				}
				smartStartTag(A_NS, "ext", "a") {
					attr("cx", cx.toString())
					attr("cy", cy.toString())
				}
			}
			smartStartTag(A_NS, "prstGeom", "a") {
				attr("prst", "rect")
				smartStartTag(A_NS, "avLst", "a") {}
			}
		}
	}

/**
 * The size, in EMUs, of an image of [aspectRatio] (height over width): [widthFraction] of the text
 * width, scaling a small image up, then narrowed if need be to keep within [maxHeightFraction] of the
 * text height.
 */
private fun imageExtent(aspectRatio: Float, widthFraction: Float, maxHeightFraction: Float): Pair<Long, Long> {
	val textWidth = (PAGE_WIDTH_TWIPS - PAGE_MARGIN_TWIPS * 2) * EMU_PER_TWIP
	val maxHeight = (PAGE_HEIGHT_TWIPS - PAGE_MARGIN_TWIPS * 2) * EMU_PER_TWIP * maxHeightFraction.toDouble()
	var width = textWidth * widthFraction.toDouble()
	var height = width * aspectRatio
	if (height > maxHeight) {
		height = maxHeight
		width = maxHeight / aspectRatio
	}
	// A crafted header can give an aspect ratio extreme enough to round one side to nothing.
	return width.roundToLong().coerceAtLeast(1) to height.roundToLong().coerceAtLeast(1)
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
