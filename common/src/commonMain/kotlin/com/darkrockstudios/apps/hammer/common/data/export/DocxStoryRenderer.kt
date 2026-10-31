package com.darkrockstudios.apps.hammer.common.data.export

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
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
private const val REL_TYPE_HEADER =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
private const val REL_TYPE_IMAGE =
	"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"

private const val HEADER_CONTENT_TYPE =
	"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"

private const val BULLET_NUM_ID = 1
private const val BULLET_ABSTRACT_ID = 0
private const val DECIMAL_ABSTRACT_ID = 1

/**
 * Relationship ids rId1 to rId6 in document.xml.rels are styles, numbering, settings, the font table,
 * the running header and the empty first-page header; images follow.
 */
private const val RUNNING_HEADER_REL_ID = "rId5"
private const val FIRST_PAGE_HEADER_REL_ID = "rId6"
private const val FIRST_BODY_REL_ID = 7

/** The declaration Word writes at the top of every part. */
private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

/** US Letter with one-inch margins, in twips. */
private const val PAGE_WIDTH_TWIPS = 12240
private const val PAGE_HEIGHT_TWIPS = 15840
private const val PAGE_MARGIN_TWIPS = 1440
private const val TEXT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - PAGE_MARGIN_TWIPS * 2

private const val EMU_PER_TWIP = 635L

private const val MODERN_FONT = "Times New Roman"
private const val CLASSIC_FONT = "Courier New"

/** Code is set in Courier New whichever manuscript font the body uses. */
private const val CODE_FONT = "Courier New"

/** `w:line` values with `lineRule="auto"`, in 240ths of a line. */
private const val SINGLE_SPACED = "240"
private const val DOUBLE_SPACED = "480"

/** One double-spaced line, in twips: the space a blank line in the manuscript would take. */
private const val DOUBLE_SPACE_TWIPS = 480

/**
 * About the height of a single-spaced 12pt line of Times New Roman or Courier New, in twips. Only used
 * to estimate how far the contact block reaches down the first page, so the title can be placed below it.
 */
private const val SINGLE_LINE_TWIPS = 276

/**
 * Space above a novel's chapter heading, in twips. With the top margin it puts the heading about 3.3"
 * down the page, roughly a third of the way.
 */
private const val CHAPTER_DROP_TWIPS = 3360

private const val FIRST_LINE_INDENT = "720"

/** The longest title the running header prints whole; a longer one is cut to its leading words. */
private const val HEADER_TITLE_MAX_LENGTH = 30

private val WHITESPACE = Regex("\\s+")
private val GENERATIONAL_SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv")
private val LEADING_ARTICLES = setOf("the", "a", "an")

/**
 * Renders the story as an OOXML WordprocessingML (.docx) package in Standard Manuscript Format
 * (William Shunn's Modern guide): US Letter, one-inch margins, 12pt black text in Times New Roman, or
 * Courier New when [ExportOptions.manuscriptClassic]. Body text is double spaced with a half-inch
 * first-line indent and no space between paragraphs. [ExportOptions.font] and
 * [ExportOptions.keepBlankLines] do not apply.
 *
 * Page 1 opens with [contact], single spaced at the top left, with [ExportStrings.wordCount] flush
 * right on its first line. Every later page carries a running header, flush right: the byline
 * surname (or failing that the legal name's), the title, and the page number. A title longer than
 * [HEADER_TITLE_MAX_LENGTH] characters is cut to its leading words there.
 *
 * The novel layout gives the title and byline a title page of their own, centred about halfway down,
 * and numbers the text pages from 1. Each chapter starts a new page with its heading about a third of
 * the way down. The short story layout sets the title and byline about a third of the way down page 1
 * and starts the text two double-spaced lines below; later chapter headings run on in the flow, and a
 * story of a single chapter prints no chapter heading at all.
 *
 * Each chapter's scenes are parsed separately, with lone blank lines dropped. A centred `#` marks the
 * break between scenes, a markdown rule, and a run of blank lines; consecutive breaks collapse to one
 * and none falls at a chapter's start or end. [ExportStrings.endMarker] closes the manuscript; with no
 * [chapters] it follows the title, on a page of its own in the novel layout. Links are written as
 * their text.
 *
 * A project image on a line of its own is embedded from [images], centred at 70% of the text width
 * and no taller than 60% of the text height. One missing there, an SVG, or one [exportRaster] cannot
 * read is written as its name. There is no cover.
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
	contact: ManuscriptContact = ManuscriptContact(),
	clock: Clock = Clock.System,
) {
	val authorName = projectData.authorName?.takeIf { it.isNotBlank() }
	val font = if (options.manuscriptClassic) CLASSIC_FONT else MODERN_FONT
	val surname = surname(authorName) ?: surname(contact.legalName)
	val headerText = listOfNotNull(surname, headerTitle(projectName)).joinToString(" / ", postfix = " / ")

	// The body walk collects the images and ordered-list instances that document.xml.rels and
	// numbering.xml must declare, so it runs first.
	val ctx = DocxRenderContext(imageNames, images)
	val documentXml = buildXmlPart {
		writeDocument(it, ctx, projectName, strings, chapters, options, contact)
	}

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
			InMemoryZipEntry("word/styles.xml", buildXmlPart { writeStyles(it, font) }),
			InMemoryZipEntry("word/numbering.xml", buildXmlPart { writeNumbering(it, ctx.orderedListLevels) }),
			InMemoryZipEntry("word/settings.xml", buildXmlPart(::writeSettings)),
			InMemoryZipEntry("word/fontTable.xml", buildXmlPart { writeFontTable(it, font) }),
			InMemoryZipEntry("word/header1.xml", buildXmlPart { writeHeader(it, headerText) }),
			InMemoryZipEntry("word/header2.xml", buildXmlPart { writeHeader(it, text = null) }),
			InMemoryZipEntry(
				"word/_rels/document.xml.rels",
				buildXmlPart { writeDocumentRels(it, ctx.relationships) },
			),
			InMemoryZipEntry("word/document.xml", documentXml),
		) + ctx.media.map { InMemoryZipEntry("word/${it.target}", it.raster.bytes) },
		stored = true,
	)
}

/**
 * The last word of [name], passing over a trailing generational suffix such as "Jr." or "III".
 * Multi-word surnames such as "Le Guin" come out as their last word.
 */
private fun surname(name: String?): String? {
	val words = name?.trim()?.split(WHITESPACE)?.map { it.trimEnd(',') }?.filter { it.isNotEmpty() } ?: return null
	val suffixed = words.size > 1 && words.last().removeSuffix(".").lowercase() in GENERATIONAL_SUFFIXES
	return (if (suffixed) words.dropLast(1) else words).lastOrNull()
}

/**
 * [title] whole when it fits in [HEADER_TITLE_MAX_LENGTH] characters. Otherwise its leading words up
 * to that length, after any leading English article, and always at least one word.
 */
private fun headerTitle(title: String): String {
	val trimmed = title.trim()
	if (trimmed.length <= HEADER_TITLE_MAX_LENGTH) return trimmed
	val words = trimmed.split(WHITESPACE).let {
		if (it.size > 1 && it.first().lowercase() in LEADING_ARTICLES) it.drop(1) else it
	}
	val shortened = StringBuilder(words.first())
	for (word in words.drop(1)) {
		if (shortened.length + 1 + word.length > HEADER_TITLE_MAX_LENGTH) break
		shortened.append(' ').append(word)
	}
	return shortened.toString()
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
private class DocxRelationship(val type: String, val target: String)

/** An image part, at [target] relative to `word/`, and the id of the relationship that points at it. */
private class DocxMedia(val target: String, val relId: String, val raster: ExportRaster)

private class DocxRenderContext(val imageNames: Map<Int, String>, val images: Map<Int, ExportImage>) {
	/** Images in the order the body first refers to them. */
	val relationships = mutableListOf<DocxRelationship>()

	val media = mutableListOf<DocxMedia>()

	/** Null for an image that is written as its name. */
	private val mediaByImageId = mutableMapOf<Int, DocxMedia?>()

	private var drawingCount = 0

	/** One entry per ordered list in the document, holding its indent level; entry index maps to its numId. */
	val orderedListLevels = mutableListOf<Int>()

	/** The part for [imageId], added the first time the body shows it, or null when it has none. */
	fun mediaFor(imageId: Int): DocxMedia? {
		if (imageId !in mediaByImageId) {
			mediaByImageId[imageId] = images[imageId]?.let(::exportRaster)?.let { raster ->
				val target = "media/image${media.size + 1}.${raster.format.extension}"
				val relId = addRelationship(DocxRelationship(REL_TYPE_IMAGE, target))
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

/** A `w:spacing` with only the attributes given. */
private fun XmlWriter.spacing(before: Int? = null, after: Int? = null, line: String? = null) = w("spacing") {
	before?.let { wAttr("before", it.toString()) }
	after?.let { wAttr("after", it.toString()) }
	line?.let {
		wAttr("line", it)
		wAttr("lineRule", "auto")
	}
}

/** A run of plain [text]. */
private fun XmlWriter.textRun(text: String) = w("r") {
	w("t") {
		preserveSpace()
		text(text)
	}
}

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
		override("/word/header1.xml", HEADER_CONTENT_TYPE)
		override("/word/header2.xml", HEADER_CONTENT_TYPE)
		override("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml")
		override(
			"/docProps/app.xml",
			"application/vnd.openxmlformats-officedocument.extended-properties+xml"
		)
	}
}

private fun XmlWriter.relationship(id: String, type: String, target: String) =
	smartStartTag(PACKAGE_RELS_NS, "Relationship", "") {
		attr("Id", id)
		attr("Type", type)
		attr("Target", target)
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
		relationship(RUNNING_HEADER_REL_ID, REL_TYPE_HEADER, "header1.xml")
		relationship(FIRST_PAGE_HEADER_REL_ID, REL_TYPE_HEADER, "header2.xml")
		relationships.forEachIndexed { index, it ->
			relationship("rId${index + FIRST_BODY_REL_ID}", it.type, it.target)
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
private fun writeFontTable(writer: XmlWriter, bodyFont: String) {
	val fonts = listOf(bodyFont, CODE_FONT).distinct()

	writer.smartStartTag(W_NS, "fonts", "w") {
		fonts.forEach { name ->
			val monospace = name == CLASSIC_FONT
			w("font") {
				wAttr("name", name)
				wVal("charset", "00")
				wVal("family", if (monospace) "modern" else "roman")
				wVal("pitch", if (monospace) "fixed" else "variable")
			}
		}
	}
}

/**
 * [text] flush right, followed by the page number; with no [text], an empty header. Single spaced, so
 * the double-spaced document default does not push it into the body.
 */
private fun writeHeader(writer: XmlWriter, text: String?) {
	fun XmlWriter.fieldChar(type: String) = w("r") { w("fldChar") { wAttr("fldCharType", type) } }

	writer.smartStartTag(W_NS, "hdr", "w") {
		w("p") {
			w("pPr") {
				spacing(line = SINGLE_SPACED)
				if (text != null) wVal("jc", "right")
			}
			if (text != null) {
				textRun(text)
				fieldChar("begin")
				w("r") {
					w("instrText") {
						preserveSpace()
						text(" PAGE ")
					}
				}
				fieldChar("separate")
				textRun("1")
				fieldChar("end")
			}
		}
	}
}

private fun writeStyles(writer: XmlWriter, bodyFont: String) {
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
		default: Boolean = false,
		body: XmlWriter.() -> Unit = {},
	) = w("style") {
		wAttr("type", "paragraph")
		if (default) wAttr("default", "1")
		wAttr("styleId", styleId)
		wVal("name", name)
		if (styleId != "Normal") wVal("basedOn", "Normal")
		body()
	}

	writer.smartStartTag(W_NS, "styles", "w") {
		w("docDefaults") {
			w("rPrDefault") {
				w("rPr") {
					rFonts(bodyFont)
					wVal("color", "auto")
					size(24)
				}
			}
			w("pPrDefault") {
				w("pPr") {
					spacing(after = 0, line = DOUBLE_SPACED)
				}
			}
		}

		style("Normal", "Normal", default = true)

		style("BodyText", "Body Text") {
			w("pPr") {
				w("ind") { wAttr("firstLine", FIRST_LINE_INDENT) }
			}
		}

		// Chapter headings: plain 12pt text like the rest of the manuscript; the outline level keeps
		// them in the navigation pane.
		style("Heading1", "heading 1") {
			wVal("next", "BodyText")
			w("pPr") {
				w("keepNext")
				wVal("jc", "center")
				wVal("outlineLvl", "0")
			}
		}

		// Headings an author typed into a scene.
		for (level in 2..6) {
			style("Heading$level", "heading $level") {
				wVal("next", "BodyText")
				w("pPr") {
					w("keepNext")
					wVal("outlineLvl", (level - 1).toString())
				}
				w("rPr") {
					w("b")
				}
			}
		}

		// Manuscripts set a block quote off by its indent alone.
		style("Quote", "Quote") {
			w("pPr") {
				w("ind") {
					wAttr("left", "720")
					wAttr("right", "720")
				}
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
	options: ExportOptions,
	contact: ManuscriptContact,
) {
	val novel = !options.manuscriptShortStory
	writer.smartStartTag(W_NS, "document", "w") {
		namespaceAttr("r", R_NS)
		namespaceAttr("wp", WP_NS)
		namespaceAttr("a", A_NS)
		namespaceAttr("pic", PIC_NS)
		w("body") {
			val contactLines = writeContactBlock(contact, strings.wordCount)
			writeTitle(projectName, strings.authorByline, contactLines, novel)
			val headed = novel || chapters.size > 1
			chapters.forEachIndexed { index, chapter ->
				if (headed) writeChapterHeading(chapterTitle(index, chapter, options.numberChapters), index, novel)
				MarkdownDocxWriter(this, ctx).render(chapterBlocks(chapter, ctx))
			}
			centredLine(strings.endMarker, pageBreakBefore = novel && chapters.isEmpty())
			w("sectPr") {
				w("headerReference") {
					wAttr("type", "default")
					attribute(R_NS, "id", "r", RUNNING_HEADER_REL_ID)
				}
				w("headerReference") {
					wAttr("type", "first")
					attribute(R_NS, "id", "r", FIRST_PAGE_HEADER_REL_ID)
				}
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
				// The title page is page 0, so the text pages count from 1.
				if (novel) w("pgNumType") { wAttr("start", "0") }
				w("titlePg")
			}
		}
	}
}

/**
 * The contact block, single spaced at the top left: legal name, each address line, phone and email,
 * leaving out blank ones. [wordCount] sits flush right on the first line, or on a line of its own when
 * there is no contact at all. Returns the number of lines written.
 */
private fun XmlWriter.writeContactBlock(contact: ManuscriptContact, wordCount: String): Int {
	val lines = buildList {
		add(contact.legalName)
		addAll(contact.address.replace("\r\n", "\n").split('\n'))
		add(contact.phone)
		add(contact.email)
	}.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }

	lines.forEachIndexed { index, line ->
		w("p") {
			w("pPr") {
				if (index == 0) {
					w("tabs") {
						w("tab") {
							wAttr("val", "right")
							wAttr("pos", TEXT_WIDTH_TWIPS.toString())
						}
					}
				}
				spacing(line = SINGLE_SPACED)
			}
			if (line.isNotEmpty()) textRun(line)
			if (index == 0) {
				w("r") { w("tab") }
				textRun(wordCount)
			}
		}
	}
	return lines.size
}

/**
 * The centred title and byline below a contact block of [contactLines] lines: halfway down the page
 * for a novel's title page, a third of the way down for a short story. A short story's text starts two
 * double-spaced lines below the last of them.
 */
private fun XmlWriter.writeTitle(projectName: String, byline: String?, contactLines: Int, novel: Boolean) {
	val titleTop = if (novel) PAGE_HEIGHT_TWIPS / 2 else PAGE_HEIGHT_TWIPS / 3
	val before = (titleTop - PAGE_MARGIN_TWIPS - contactLines * SINGLE_LINE_TWIPS).coerceAtLeast(DOUBLE_SPACE_TWIPS)
	val textGap = if (novel) null else DOUBLE_SPACE_TWIPS
	centredLine(projectName, before = before, after = if (byline == null) textGap else null)
	byline?.let { centredLine(it, after = textGap) }
}

/**
 * A chapter heading: on a new page about a third of the way down in a novel, running on in a short
 * story with a double-spaced line above all but the first.
 */
private fun XmlWriter.writeChapterHeading(title: String, index: Int, novel: Boolean) {
	w("p") {
		w("pPr") {
			wVal("pStyle", "Heading1")
			if (novel) {
				wVal("pageBreakBefore", "true")
				spacing(before = CHAPTER_DROP_TWIPS, after = DOUBLE_SPACE_TWIPS)
			} else if (index > 0) {
				spacing(before = DOUBLE_SPACE_TWIPS)
			}
		}
		textRun(title)
	}
}

/** A centred, unindented paragraph of [text]. */
private fun XmlWriter.centredLine(
	text: String,
	before: Int? = null,
	after: Int? = null,
	pageBreakBefore: Boolean = false,
) {
	w("p") {
		w("pPr") {
			if (pageBreakBefore) wVal("pageBreakBefore", "true")
			if (before != null || after != null) spacing(before = before, after = after)
			wVal("jc", "center")
		}
		textRun(text)
	}
}

/**
 * The blocks of [chapter], scene by scene, with every break between passages as a [ProseBlock.Rule]:
 * one between scenes, a markdown rule, and a run of blank lines. Consecutive breaks collapse to one,
 * and breaks at the chapter's start or end are dropped, so an empty scene leaves no trace.
 */
private fun chapterBlocks(chapter: StoryChapter, ctx: DocxRenderContext): List<ProseBlock> {
	val blocks = mutableListOf<ProseBlock>()
	chapter.scenes.forEachIndexed { index, scene ->
		val parsed = parseProseMarkdown(scene, keepBlankLines = false, ctx.imageNames::get)
		(if (index > 0) listOf(ProseBlock.Rule) + parsed else parsed).forEach { block ->
			val isBreak = block == ProseBlock.Rule || block == ProseBlock.Blank
			if (!isBreak) {
				blocks += block
			} else if (blocks.isNotEmpty() && blocks.last() != ProseBlock.Rule) {
				blocks += ProseBlock.Rule
			}
		}
	}
	if (blocks.lastOrNull() == ProseBlock.Rule) blocks.removeAt(blocks.lastIndex)
	return blocks
}

/**
 * Renders a chapter's blocks into WordprocessingML, streaming `<w:p>` / `<w:r>` into [writer]. Block
 * kinds map onto paragraph styles; inline spans become runs, with `\n` hard breaks split into
 * `<w:br/>`. A [ProseBlock.Rule] is a scene break.
 */
private class MarkdownDocxWriter(
	private val writer: XmlWriter,
	private val ctx: DocxRenderContext,
) {
	fun render(blocks: List<ProseBlock>) {
		blocks.forEach { block ->
			when (block) {
				is ProseBlock.Paragraph -> paragraph(style = null, numId = null) { runs(block.spans) }

				is ProseBlock.Image -> {
					val media = ctx.mediaFor(block.imageId)
					if (media != null) {
						picture(block.name, media)
					} else {
						paragraph(style = null, numId = null) { runs(block.spans) }
					}
				}

				// The first heading level belongs to chapters.
				is ProseBlock.Heading ->
					paragraph(style = "Heading${block.level.coerceAtLeast(2)}", numId = null) { runs(block.spans) }

				is ProseBlock.Listing -> {
					val numbering = DocxListNumbering(ctx)
					block.items.forEach { item ->
						val (numId, ilvl) = numbering.numbering(item)
						paragraph(style = null, numId = numId, ilvl = ilvl) { runs(item.spans) }
					}
				}

				is ProseBlock.Quote -> block.paragraphs.forEach { paragraph(style = "Quote", numId = null) { runs(it) } }
				is ProseBlock.CodeBlock -> codeBlockLines(block.code).forEach { codeParagraph(it) }
				ProseBlock.Rule, ProseBlock.Blank -> writer.centredLine("#")
				is ProseBlock.Table -> tableFallback(block)
			}
		}
	}

	private fun runs(spans: List<ProseSpan>) = spans.forEach(::emitSpan)

	private fun emitSpan(span: ProseSpan) {
		// A hard break rides in span text as '\n'; emit a <w:br/> between the surrounding fragments.
		span.text.split("\n").forEachIndexed { index, part ->
			if (index > 0) writer.w("r") { w("br") }
			if (part.isNotEmpty()) emitRun(part, span)
		}
	}

	private fun emitRun(text: String, span: ProseSpan) {
		writer.w("r") {
			if (span.code || span.bold || span.italic || span.strikethrough) {
				w("rPr") {
					if (span.code) {
						w("rFonts") {
							wAttr("ascii", CODE_FONT)
							wAttr("hAnsi", CODE_FONT)
							wAttr("cs", CODE_FONT)
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
			this@MarkdownDocxWriter.emitRun(line, ProseSpan(text = line, code = true))
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

	private fun picture(name: String, media: DocxMedia) {
		val (cx, cy) = imageExtent(media.raster.aspectRatio, IMAGE_WIDTH_FRACTION, MAX_IMAGE_HEIGHT_FRACTION)
		writer.picture(name = name, media = media, drawingId = ctx.nextDrawingId(), cx = cx, cy = cy)
	}

	private fun paragraph(
		style: String?,
		numId: Int?,
		ilvl: Int = 0,
		body: () -> Unit,
	) {
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
 * A centred paragraph holding [media] inline at [cx] by [cy] EMUs, described by [name]. Single spaced,
 * since double spacing would add half the image's height again below it.
 */
private fun XmlWriter.picture(
	name: String,
	media: DocxMedia,
	drawingId: Int,
	cx: Long,
	cy: Long,
) {
	w("p") {
		w("pPr") {
			spacing(line = SINGLE_SPACED)
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
	val textWidth = TEXT_WIDTH_TWIPS * EMU_PER_TWIP
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
