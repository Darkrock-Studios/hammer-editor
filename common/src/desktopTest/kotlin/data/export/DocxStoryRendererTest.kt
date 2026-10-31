package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.writeStoryAsDocx
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import okio.Buffer
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import utils.unzip
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class DocxStoryRendererTest {

	private fun renderBytes(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Test Author"),
		options: ExportOptions = ExportOptions(),
		imageNames: Map<Int, String> = emptyMap(),
		images: Map<Int, ExportImage> = emptyMap(),
	): ByteArray {
		val buffer = Buffer()
		val author = projectData.authorName?.takeIf { it.isNotBlank() }
		writeStoryAsDocx(
			sink = buffer,
			projectName = projectName,
			projectData = projectData,
			chapters = chapters,
			strings = ExportStrings(
				contentsTitle = "Contents",
				authorByline = author?.let { "by $it" },
				wordCount = { "about $it words" },
				endMarker = "END",
			),
			options = options,
			imageNames = imageNames,
			images = images,
			clock = PINNED_CLOCK,
		)
		return buffer.readByteArray()
	}

	private fun render(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Test Author"),
		options: ExportOptions = ExportOptions(),
		imageNames: Map<Int, String> = emptyMap(),
		images: Map<Int, ExportImage> = emptyMap(),
	): XWPFDocument = XWPFDocument(
		ByteArrayInputStream(renderBytes(chapters, projectName, projectData, options, imageNames, images)),
	)

	private fun XWPFDocument.paragraphTexts(): List<String> = paragraphs.map { it.text }

	/** Paragraphs of the chapter body: everything after the chapter's Heading1 paragraph. */
	private fun XWPFDocument.bodyParagraphs(): List<XWPFParagraph> {
		val headingIndex = paragraphs.indexOfFirst { it.style == "Heading1" }
		assertTrue(headingIndex >= 0, "Document should contain a Heading1 chapter heading")
		return paragraphs.drop(headingIndex + 1)
	}

	@Test
	fun `document opens in POI with title and author metadata`() {
		val doc = render(listOf(StoryChapter("Alpha", "Some text.")))

		assertEquals("Test Project", doc.properties.coreProperties.title)
		assertEquals("Test Author", doc.properties.coreProperties.creator)
	}

	@Test
	fun `extended metadata identifies Hammer as the generating application`() {
		val doc = render(listOf(StoryChapter("Alpha", "Some text.")))

		val extended = doc.properties.extendedProperties.underlyingProperties
		assertEquals("Hammer", extended.application)
		assertTrue(
			extended.appVersion.isNotBlank(),
			"App version metadata should be present",
		)
	}

	@Test
	fun `title page shows project name with Title style and author byline`() {
		val doc = render(listOf(StoryChapter("Alpha", "Some text.")))

		val titlePara = doc.paragraphs.first()
		assertEquals("Title", titlePara.style)
		assertEquals("Test Project", titlePara.text)
		assertTrue(
			doc.paragraphTexts().contains("by Test Author"),
			"Expected an author byline paragraph"
		)
	}

	@Test
	fun `contents page hyperlinks resolve to chapter bookmarks`() {
		val doc = render(
			listOf(
				StoryChapter("Alpha", "First."),
				StoryChapter("Beta", "Second."),
			)
		)

		val anchors =
			doc.paragraphs.flatMap { it.ctp.hyperlinkList.mapNotNull { link -> link.anchor } }
		assertEquals(listOf("chapter1", "chapter2"), anchors)

		val bookmarks =
			doc.paragraphs.flatMap { it.ctp.bookmarkStartList.map { mark -> mark.name } }
		assertTrue(
			bookmarks.containsAll(listOf("chapter1", "chapter2")),
			"Bookmarks for both chapters, got $bookmarks"
		)
	}

	@Test
	fun `chapter headings use Heading1 with page break before`() {
		val doc = render(
			listOf(
				StoryChapter("Alpha", "First."),
				StoryChapter("Beta", "Second."),
			),
			options = ExportOptions(numberChapters = true),
		)

		val headings = doc.paragraphs.filter { it.style == "Heading1" }
		assertEquals(listOf("1. Alpha", "2. Beta"), headings.map { it.text })
		assertTrue(headings.all { it.isPageBreak }, "Chapter headings should start on a new page")
	}

	@Test
	fun `chapters are unnumbered by default in headings and contents`() {
		val doc = render(
			listOf(
				StoryChapter("Prologue", "First."),
				StoryChapter("Chapter 1", "Second."),
			),
		)

		val headings = doc.paragraphs.filter { it.style == "Heading1" }
		assertEquals(listOf("Prologue", "Chapter 1"), headings.map { it.text })

		val contents = doc.paragraphs.filter { it.ctp.hyperlinkList.any { link -> link.anchor != null } }
		assertEquals(listOf("Prologue", "Chapter 1"), contents.map { it.text })
	}

	@Test
	fun `the chosen font becomes the document default`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "Some text.")),
			options = ExportOptions(font = ExportFont.TimesNewRoman),
		)

		val fonts = doc.styles.ctStyles.docDefaults.rPrDefault.rPr.rFontsList.single()
		assertEquals("Times New Roman", fonts.ascii)
		assertEquals("Times New Roman", fonts.hAnsi)
	}

	@Test
	fun `blank lines between paragraphs become empty paragraphs only when kept`() {
		val chapters = listOf(StoryChapter("Alpha", "First passage.\n\nSecond passage."))

		val kept = render(chapters).bodyParagraphs().map { it.text }
		assertEquals(listOf("First passage.", "", "Second passage."), kept)

		val skipped = render(chapters, options = ExportOptions(keepBlankLines = false))
			.bodyParagraphs().map { it.text }
		assertEquals(listOf("First passage.", "Second passage."), skipped)
	}

	@Test
	fun `bold and italic markdown become formatted runs`() {
		val doc =
			render(listOf(StoryChapter("Alpha", "plain **bold** *italic* **nested *both* here**")))

		val runs = doc.bodyParagraphs().flatMap { it.runs }
		val bold = runs.first { it.text() == "bold" }
		assertTrue(bold.isBold)
		assertTrue(!bold.isItalic)

		val italic = runs.first { it.text() == "italic" }
		assertTrue(italic.isItalic)
		assertTrue(!italic.isBold)

		val both = runs.first { it.text() == "both" }
		assertTrue(both.isBold)
		assertTrue(both.isItalic)

		val plain = runs.first { it.text().contains("plain") }
		assertTrue(!plain.isBold)
		assertTrue(!plain.isItalic)
	}

	@Test
	fun `markdown strikethrough becomes a struck-through run`() {
		val doc = render(listOf(StoryChapter("Alpha", "This is ~~gone~~ now.")))

		val runs = doc.bodyParagraphs().flatMap { it.runs }
		val struck = runs.first { it.text() == "gone" }
		assertTrue(struck.isStrikeThrough, "GFM strikethrough should set the run's strike property")

		val plain = runs.first { it.text().contains("now") }
		assertTrue(!plain.isStrikeThrough)
	}

	@Test
	fun `markdown headings map to heading styles`() {
		val doc =
			render(listOf(StoryChapter("Alpha", "## Section\n\nBody text.\n\n### Subsection")))

		val styles = doc.bodyParagraphs().associate { it.text to it.style }
		assertEquals("Heading2", styles["Section"])
		assertEquals("Heading3", styles["Subsection"])
	}

	@Test
	fun `bullet and ordered lists get distinct numbering`() {
		val markdown = """
			- apple
			- banana

			1. first
			2. second
		""".trimIndent()
		val doc = render(listOf(StoryChapter("Alpha", markdown)))

		val byText = doc.bodyParagraphs().associateBy { it.text }
		val apple = assertNotNull(byText["apple"])
		val banana = assertNotNull(byText["banana"])
		val first = assertNotNull(byText["first"])

		assertNotNull(apple.numID, "Bullet items should reference a numbering definition")
		assertEquals(apple.numID, banana.numID)
		assertNotNull(first.numID, "Ordered items should reference a numbering definition")
		assertNotEquals(
			apple.numID,
			first.numID,
			"Bullet and ordered lists should use different numbering"
		)
	}

	@Test
	fun `nested list items are indented a level deeper`() {
		val markdown = "- parent\n    - child"
		val doc = render(listOf(StoryChapter("Alpha", markdown)))

		val byText = doc.bodyParagraphs().associateBy { it.text }
		val parent = assertNotNull(byText["parent"])
		val child = assertNotNull(byText["child"])

		assertEquals(0, parent.numIlvl?.toInt() ?: 0)
		assertEquals(1, assertNotNull(child.numIlvl).toInt())
	}

	@Test
	fun `body paragraphs use the BodyText style with a first-line indent`() {
		val markdown = "First paragraph.\n\nSecond paragraph.\n\n- list item"
		val doc = render(listOf(StoryChapter("Alpha", markdown)))

		val bodyText =
			assertNotNull(doc.styles.getStyle("BodyText"), "BodyText style must be defined")
		val firstLine = bodyText.ctStyle.pPr?.ind?.firstLine
		assertEquals(
			360,
			(firstLine as? java.math.BigInteger)?.toInt(),
			"BodyText should indent the first line"
		)

		val byText = doc.bodyParagraphs().associateBy { it.text }
		assertEquals("BodyText", assertNotNull(byText["First paragraph."]).style)
		assertEquals("BodyText", assertNotNull(byText["Second paragraph."]).style)
		assertEquals(
			null,
			assertNotNull(byText["list item"]).style,
			"List items should not get the indented body style"
		)
	}

	@Test
	fun `blockquotes map to the Quote style`() {
		val doc = render(listOf(StoryChapter("Alpha", "> quoted wisdom")))

		val quote = doc.bodyParagraphs().first { it.text == "quoted wisdom" }
		assertEquals("Quote", quote.style)
	}

	@Test
	fun `inline code and code blocks use a monospace font`() {
		val markdown = "Call `doThing()` now.\n\n```\nval x = 1\n```"
		val doc = render(listOf(StoryChapter("Alpha", markdown)))

		val runs = doc.bodyParagraphs().flatMap { it.runs }
		val inline = runs.first { it.text() == "doThing()" }
		assertEquals("Consolas", inline.fontFamily)

		val block =
			doc.bodyParagraphs().flatMap { it.runs }.first { it.text().contains("val x = 1") }
		assertEquals("Consolas", block.fontFamily)
	}

	@Test
	fun `external links become hyperlinks with the target url`() {
		val doc = render(
			listOf(
				StoryChapter(
					"Alpha",
					"Visit [the site](https://example.com/page) today."
				)
			)
		)

		val linkRun = doc.bodyParagraphs().flatMap { it.runs }
			.filterIsInstance<XWPFHyperlinkRun>()
			.first()
		assertEquals("the site", linkRun.text())
		assertEquals("https://example.com/page", linkRun.getHyperlink(doc).url)
	}

	@Test
	fun `thematic break renders as a bordered empty paragraph`() {
		val doc = render(listOf(StoryChapter("Alpha", "before\n\n---\n\nafter")))

		val bordered = doc.bodyParagraphs().any { it.ctp.pPr?.isSetPBdr == true }
		assertTrue(bordered, "Expected a paragraph with a border for the thematic break")
	}

	@Test
	fun `xml special characters survive the round trip`() {
		val doc = render(
			chapters = listOf(
				StoryChapter(
					"Q & A <chapter>",
					"Ampersands & angles <like> \"these\" 'ones'."
				)
			),
			projectName = "Tom & Jerry's <Story>",
		)

		assertEquals("Tom & Jerry's <Story>", doc.properties.coreProperties.title)
		assertEquals("Tom & Jerry's <Story>", doc.paragraphs.first().text)

		val heading = doc.paragraphs.first { it.style == "Heading1" }
		assertEquals("Q & A <chapter>", heading.text)

		val texts = doc.paragraphTexts()
		assertTrue(
			texts.any { it.contains("Ampersands & angles <like> \"these\" 'ones'.") },
			"Body text with XML metacharacters should round-trip, got $texts",
		)
	}

	@Test
	fun `theme colors are applied to heading styles`() {
		val doc = render(
			chapters = listOf(StoryChapter("Alpha", "text")),
			projectData = ProjectData(
				authorName = "Test Author",
				theme = ProjectTheme(primary = "#FF112233", secondary = "#FF445566"),
			),
		)

		val heading1 =
			assertNotNull(doc.styles.getStyle("Heading1"), "Heading1 style must be defined")
		assertTrue(
			heading1.ctStyle.toString().contains("112233"),
			"Heading1 should carry the primary theme color"
		)

		val heading2 =
			assertNotNull(doc.styles.getStyle("Heading2"), "Heading2 style must be defined")
		assertTrue(
			heading2.ctStyle.toString().contains("445566"),
			"Heading2 should carry the secondary theme color"
		)
	}

	@Test
	fun `no author and no chapters still produces a valid document`() {
		val doc = render(
			chapters = emptyList(),
			projectData = ProjectData(authorName = null),
		)

		assertEquals("Test Project", doc.properties.coreProperties.title)
		assertEquals("Test Project", doc.paragraphs.first().text)
		assertTrue(
			doc.paragraphTexts().none { it.startsWith("by ") },
			"No byline expected without an author"
		)
	}

	@Test
	fun `every authored line becomes its own paragraph`() {
		val doc = render(listOf(StoryChapter("Alpha", "line one\nline two")))

		val texts = doc.bodyParagraphs().map { it.text }
		assertTrue(
			texts.contains("line one") && texts.contains("line two"),
			"Authored lines should each stand alone, got $texts",
		)
	}

	@Test
	fun `a line that runs on into another loses the gap between them`() {
		val doc = render(listOf(StoryChapter("Alpha", "line one\nline two")))

		val first = doc.bodyParagraphs().first { it.text == "line one" }
		assertEquals(
			0,
			(first.ctp.pPr?.spacing?.after as? java.math.BigInteger)?.toInt(),
			"A line followed by more prose must not push the next line down",
		)
	}

	@Test
	fun `a blank line between passages becomes an empty paragraph`() {
		val doc = render(listOf(StoryChapter("Alpha", "First passage.\n\nSecond passage.")))

		val texts = doc.bodyParagraphs().map { it.text }
		val first = texts.indexOf("First passage.")
		val second = texts.indexOf("Second passage.")
		assertEquals(first + 2, second, "Expected one empty paragraph between the passages, got $texts")
		assertEquals("", texts[first + 1])
	}

	@Test
	fun `a project image is written as its current name, or not at all without one`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "![image](hammer://image/3)\n\n![Old](hammer://image/9)")),
			imageNames = mapOf(3 to "Cheshire Cat"),
		)

		assertEquals(listOf("Cheshire Cat"), doc.bodyParagraphs().map { it.text }.filter { it.isNotBlank() })
	}

	@Test
	fun `an image with no file to embed is written exactly as a line of its name`() {
		fun body(markdown: String) = render(
			listOf(StoryChapter("Alpha", markdown)),
			imageNames = mapOf(3 to "Cheshire Cat"),
		).bodyParagraphs().map { it.ctp.xmlText() }

		assertEquals(
			body("Before.\n\nCheshire Cat\nAfter."),
			body("Before.\n\n![image](hammer://image/3)\nAfter."),
		)
	}

	private fun parseXml(bytes: ByteArray): Document =
		DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
			.newDocumentBuilder()
			.parse(ByteArrayInputStream(bytes))

	private fun Document.elements(namespace: String, name: String): List<Element> =
		getElementsByTagNameNS(namespace, name).let { nodes -> List(nodes.length) { nodes.item(it) as Element } }

	private fun Element.child(namespace: String, name: String): Element =
		getElementsByTagNameNS(namespace, name).item(0) as Element

	private fun image(id: Int, name: String, extension: String, bytes: ByteArray): Pair<Int, ExportImage> =
		id to ExportImage(id, name, extension, bytes)

	/** The package of one chapter showing each of [ids] on a line of its own. */
	private fun renderImages(ids: List<Int>, images: Map<Int, ExportImage>): Map<String, ByteArray> {
		val markdown = ids.joinToString("\n\n", prefix = "Before.\n\n", postfix = "\n\nAfter.") {
			"![image](hammer://image/$it)"
		}
		val names = images.mapValues { it.value.name }
		return unzip(renderBytes(listOf(StoryChapter("Alpha", markdown)), imageNames = names, images = images))
	}

	private class Drawing(val cx: Long, val cy: Long, val embed: String, val docPrId: String, val name: String)

	private fun drawings(parts: Map<String, ByteArray>): List<Drawing> =
		parseXml(assertNotNull(parts["word/document.xml"])).elements(WP_NS, "inline").map { inline ->
			val extent = inline.child(WP_NS, "extent")
			val docPr = inline.child(WP_NS, "docPr")
			Drawing(
				cx = extent.getAttribute("cx").toLong(),
				cy = extent.getAttribute("cy").toLong(),
				embed = inline.child(A_NS, "blip").getAttributeNS(R_NS, "embed"),
				docPrId = docPr.getAttribute("id"),
				name = docPr.getAttribute("name"),
			)
		}

	/** The relationships of document.xml, by id, in document order. */
	private fun documentRels(parts: Map<String, ByteArray>): Map<String, Element> =
		parseXml(assertNotNull(parts["word/_rels/document.xml.rels"]))
			.elements(PACKAGE_RELS_NS, "Relationship")
			.associateBy { it.getAttribute("Id") }

	/** The text width and height of the document's page setup, in EMUs. */
	private fun textArea(parts: Map<String, ByteArray>): Pair<Long, Long> {
		val document = parseXml(assertNotNull(parts["word/document.xml"]))
		val pgSz = document.elements(W_NS, "pgSz").single()
		val pgMar = document.elements(W_NS, "pgMar").single()
		fun Element.twips(name: String) = getAttributeNS(W_NS, name).toLong()
		val width = pgSz.twips("w") - pgMar.twips("left") - pgMar.twips("right")
		val height = pgSz.twips("h") - pgMar.twips("top") - pgMar.twips("bottom")
		return width * EMU_PER_TWIP to height * EMU_PER_TWIP
	}

	private fun contentTypeDefaults(parts: Map<String, ByteArray>): Map<String, String> =
		parseXml(assertNotNull(parts["[Content_Types].xml"]))
			.elements(CONTENT_TYPES_NS, "Default")
			.associate { it.getAttribute("Extension") to it.getAttribute("ContentType") }

	@Test
	fun `a png and a jpeg are each embedded at 70 percent of the text width`() {
		val parts = renderImages(
			listOf(3, 4),
			mapOf(image(3, "Cat", "png", encoded("png")), image(4, "Dog", "jpg", encoded("jpg"))),
		)

		assertEquals(
			listOf("word/media/image1.png", "word/media/image2.jpeg"),
			parts.keys.filter { it.startsWith("word/media/") },
		)
		val (textWidth, _) = textArea(parts)
		val width = (textWidth * 0.7).roundToLong()
		val rels = documentRels(parts)
		val drawings = drawings(parts)
		assertEquals(listOf("Cat", "Dog"), drawings.map { it.name })
		assertEquals(listOf("1", "2"), drawings.map { it.docPrId })
		drawings.zip(listOf("media/image1.png", "media/image2.jpeg")).forEach { (drawing, target) ->
			assertEquals(width, drawing.cx)
			assertEquals((width * 0.5).roundToLong(), drawing.cy)
			val rel = assertNotNull(rels[drawing.embed])
			assertEquals(IMAGE_REL_TYPE, rel.getAttribute("Type"))
			assertEquals(target, rel.getAttribute("Target"))
		}
	}

	@Test
	fun `an embedded image sits centred in place of its name`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "Before.\n\n![image](hammer://image/3)\n\nAfter.")),
			imageNames = mapOf(3 to "Cat"),
			images = mapOf(image(3, "Cat", "png", encoded("png"))),
		)

		val body = doc.bodyParagraphs()
		assertTrue(body.none { it.text == "Cat" }, "The name should not be written")
		val picture = body.single { paragraph -> paragraph.runs.any { it.embeddedPictures.isNotEmpty() } }
		assertEquals(ParagraphAlignment.CENTER, picture.alignment)
		assertEquals(1, doc.allPictures.size)
	}

	@Test
	fun `an embedded image is a paragraph gap from prose but not from a typed blank line`() {
		fun spacing(markdown: String): Pair<Int?, Int?> {
			val picture = render(
				listOf(StoryChapter("Alpha", markdown)),
				imageNames = mapOf(3 to "Cat"),
				images = mapOf(image(3, "Cat", "png", encoded("png"))),
			).bodyParagraphs().single { paragraph -> paragraph.runs.any { it.embeddedPictures.isNotEmpty() } }
			val spacing = picture.ctp.pPr?.spacing
			fun Any?.twips() = (this as? java.math.BigInteger)?.toInt()
			return spacing?.before.twips() to spacing?.after.twips()
		}

		assertEquals(160 to 160, spacing("Before.\n![image](hammer://image/3)\nAfter."))
		assertEquals(0 to 0, spacing("Before.\n\n![image](hammer://image/3)\n\nAfter."))
	}

	@Test
	fun `a tall image is held to 60 percent of the text height`() {
		val parts = renderImages(listOf(3), mapOf(image(3, "Tower", "png", encoded("png", width = 20, height = 80))))

		val (_, textHeight) = textArea(parts)
		val height = (textHeight * 0.6).roundToLong()
		val drawing = drawings(parts).single()
		assertEquals(height, drawing.cy)
		assertEquals((height / 4.0).roundToLong(), drawing.cx)
	}

	@Test
	fun `an embedded image is inline with no wrap distance and a locked aspect ratio`() {
		val parts = renderImages(listOf(3), mapOf(image(3, "Cat", "png", encoded("png"))))
		val inline = parseXml(assertNotNull(parts["word/document.xml"])).elements(WP_NS, "inline").single()

		assertEquals(listOf("0", "0", "0", "0"), listOf("distT", "distB", "distL", "distR").map(inline::getAttribute))
		val children = (0 until inline.childNodes.length).map { inline.childNodes.item(it) }.filterIsInstance<Element>()
		assertEquals(listOf("extent", "docPr", "cNvGraphicFramePr", "graphic"), children.map { it.localName })
		assertEquals("1", inline.child(A_NS, "graphicFrameLocks").getAttribute("noChangeAspect"))
		assertEquals("1", inline.child(A_NS, "picLocks").getAttribute("noChangeAspect"))
	}

	@Test
	fun `a webp is embedded as a png`() {
		val parts = renderImages(listOf(3), mapOf(image(3, "Cat", "webp", webp())))

		val png = assertNotNull(parts["word/media/image1.png"])
		assertEquals(0x89.toByte(), png[0])
		assertEquals("PNG", png.copyOfRange(1, 4).decodeToString())
		assertTrue(parts.keys.none { it.endsWith(".webp") })
		assertEquals(1, drawings(parts).size)
	}

	@Test
	fun `an svg or an unreadable image is written as its name`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		val images = mapOf(
			image(3, "Vector", "svg", svg.encodeToByteArray()),
			image(4, "Broken", "png", "not an image".encodeToByteArray()),
		)
		val bytes = renderBytes(
			listOf(StoryChapter("Alpha", "![image](hammer://image/3)\n\n![image](hammer://image/4)")),
			imageNames = images.mapValues { it.value.name },
			images = images,
		)

		val parts = unzip(bytes)
		assertTrue(parts.keys.none { it.startsWith("word/media/") })
		assertTrue(drawings(parts).isEmpty())
		assertTrue(documentRels(parts).values.none { it.getAttribute("Type") == IMAGE_REL_TYPE })
		val texts = XWPFDocument(ByteArrayInputStream(bytes)).bodyParagraphs().map { it.text }
		assertEquals(listOf("Vector", "Broken"), texts.filter { it.isNotBlank() })
	}

	@Test
	fun `an image shown twice is packaged once`() {
		val parts = renderImages(listOf(3, 3), mapOf(image(3, "Cat", "png", encoded("png"))))

		assertEquals(listOf("word/media/image1.png"), parts.keys.filter { it.startsWith("word/media/") })
		val drawings = drawings(parts)
		assertEquals(2, drawings.size)
		assertEquals(1, drawings.map { it.embed }.distinct().size)
		assertEquals(2, drawings.map { it.docPrId }.distinct().size)
		assertEquals(1, documentRels(parts).values.count { it.getAttribute("Type") == IMAGE_REL_TYPE })
	}

	@Test
	fun `content types declare only the image formats the package holds`() {
		val both = contentTypeDefaults(
			renderImages(
				listOf(3, 4),
				mapOf(image(3, "Cat", "png", encoded("png")), image(4, "Dog", "jpeg", encoded("jpg"))),
			),
		)
		assertEquals("image/png", both["png"])
		assertEquals("image/jpeg", both["jpeg"])

		val none = contentTypeDefaults(renderImages(emptyList(), emptyMap()))
		assertNull(none["png"])
		assertNull(none["jpeg"])
	}

	@Test
	fun `hyperlinks and images share one run of relationship ids`() {
		val markdown = "[one](https://example.com/1)\n\n![image](hammer://image/3)\n\n[two](https://example.com/2)"
		val bytes = renderBytes(
			listOf(StoryChapter("Alpha", markdown)),
			imageNames = mapOf(3 to "Cat"),
			images = mapOf(image(3, "Cat", "png", encoded("png"))),
		)

		val rels = documentRels(unzip(bytes))
		assertEquals((1..7).map { "rId$it" }, rels.keys.toList())
		assertEquals("https://example.com/1", rels.getValue("rId5").getAttribute("Target"))
		assertEquals("media/image1.png", rels.getValue("rId6").getAttribute("Target"))
		assertEquals("https://example.com/2", rels.getValue("rId7").getAttribute("Target"))

		val doc = XWPFDocument(ByteArrayInputStream(bytes))
		val links = doc.bodyParagraphs().flatMap { it.runs }.filterIsInstance<XWPFHyperlinkRun>()
		assertEquals(listOf("https://example.com/1", "https://example.com/2"), links.map { it.getHyperlink(doc).url })
		assertEquals(1, doc.allPictures.size)
	}

	@Test
	fun `every xml part of a package with images parses`() {
		val parts = renderImages(
			listOf(3, 4, 5),
			mapOf(
				image(3, "Cat & <Dog>", "png", encoded("png")),
				image(4, "Dog", "jpg", encoded("jpg")),
				image(5, "Bird", "webp", webp()),
			),
		)

		val xmlParts = parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }
		assertEquals(10, xmlParts.size, "Expected every package part, got ${parts.keys}")
		xmlParts.forEach { (name, bytes) -> assertNotNull(runCatching { parseXml(bytes) }.getOrNull(), name) }
		assertEquals("Cat & <Dog>", drawings(parts).first().name)
	}

	@Test
	fun `every xml part opens with the declaration Word writes`() {
		val parts = renderImages(listOf(3), mapOf(image(3, "Cat", "png", encoded("png"))))

		val xmlParts = parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }
		xmlParts.forEach { (name, bytes) ->
			assertTrue(bytes.decodeToString().startsWith(WORD_XML_DECLARATION), "$name: ${bytes.decodeToString().take(80)}")
		}
	}

	@Test
	fun `settings and the font table are related to the document and content-typed`() {
		val parts = unzip(renderBytes(listOf(StoryChapter("Alpha", "Text.")), options = ExportOptions(font = ExportFont.Arial)))

		val targets = documentRels(parts).values.associate { it.getAttribute("Type") to it.getAttribute("Target") }
		assertEquals("settings.xml", targets[SETTINGS_REL_TYPE])
		assertEquals("fontTable.xml", targets[FONT_TABLE_REL_TYPE])

		val overrides = parseXml(assertNotNull(parts["[Content_Types].xml"]))
			.elements(CONTENT_TYPES_NS, "Override")
			.associate { it.getAttribute("PartName") to it.getAttribute("ContentType") }
		assertEquals(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml",
			overrides["/word/settings.xml"],
		)
		assertEquals(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml",
			overrides["/word/fontTable.xml"],
		)

		val compat = parseXml(assertNotNull(parts["word/settings.xml"])).elements(W_NS, "compatSetting").single()
		assertEquals("compatibilityMode", compat.getAttributeNS(W_NS, "name"))
		assertEquals("http://schemas.microsoft.com/office/word", compat.getAttributeNS(W_NS, "uri"))
		assertEquals("15", compat.getAttributeNS(W_NS, "val"))

		val fonts = parseXml(assertNotNull(parts["word/fontTable.xml"])).elements(W_NS, "font")
		assertEquals(listOf("Arial", "Consolas"), fonts.map { it.getAttributeNS(W_NS, "name") })
	}

	@Test
	fun `core properties date the package to the second from the clock`() {
		val core = parseXml(assertNotNull(unzip(renderBytes(listOf(StoryChapter("Alpha", "Text."))))["docProps/core.xml"]))

		for (name in listOf("created", "modified")) {
			val date = core.elements(DCTERMS_NS, name).single()
			assertEquals("2026-10-31T12:00:00Z", date.textContent)
			assertEquals("dcterms:W3CDTF", date.getAttributeNS(XSI_NS, "type"))
			assertEquals(DCTERMS_NS, date.lookupNamespaceURI("dcterms"))
		}
	}

	@Test
	fun `no zip entry defers its sizes to a data descriptor`() {
		val bytes = renderBytes(
			listOf(StoryChapter("Alpha", "Before.\n\n![image](hammer://image/3)")),
			imageNames = mapOf(3 to "Cat"),
			images = mapOf(image(3, "Cat", "png", encoded("png"))),
		)

		val headers = localFileHeaders(bytes)
		assertEquals(unzip(bytes).keys.toList(), headers.map { it.first })
		headers.forEach { (name, flags) -> assertEquals(0, flags and 0x08, "$name sets the data descriptor flag") }
	}

	/** Each local file header's name and general purpose flags, walked from the start of the archive. */
	private fun localFileHeaders(bytes: ByteArray): List<Pair<String, Int>> {
		val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
		val headers = mutableListOf<Pair<String, Int>>()
		var offset = 0
		while (buffer.getInt(offset) == LOCAL_FILE_HEADER_SIGNATURE) {
			val flags = buffer.getShort(offset + 6).toInt() and 0xFFFF
			val compressedSize = buffer.getInt(offset + 18)
			val nameLength = buffer.getShort(offset + 26).toInt() and 0xFFFF
			val extraLength = buffer.getShort(offset + 28).toInt() and 0xFFFF
			headers += String(bytes, offset + 30, nameLength, Charsets.UTF_8) to flags
			// A descriptor-flagged header holds no size to step over.
			if (flags and 0x08 != 0) break
			offset += 30 + nameLength + extraLength + compressedSize
		}
		return headers
	}

	/** The package of a one-chapter story showing [markdown] whose project cover is image 3. */
	private fun renderCover(
		images: Map<Int, ExportImage>,
		coverImageId: Int? = 3,
		markdown: String = "Body.",
	): ByteArray = renderBytes(
		listOf(StoryChapter("Alpha", markdown)),
		projectData = ProjectData(authorName = "Test Author", coverImageId = coverImageId),
		imageNames = images.mapValues { it.value.name },
		images = images,
	)

	@Test
	fun `a cover fills the text width of the first page before the title page`() {
		val bytes = renderCover(mapOf(image(3, "Cover", "png", encoded("png", width = 40, height = 20))))

		val parts = unzip(bytes)
		val (textWidth, _) = textArea(parts)
		val drawing = drawings(parts).single()
		assertEquals(textWidth, drawing.cx)
		assertEquals(textWidth / 2, drawing.cy)
		assertEquals("Cover", drawing.name)

		val doc = XWPFDocument(ByteArrayInputStream(bytes))
		val cover = doc.paragraphs[0]
		assertEquals(1, cover.runs.sumOf { it.embeddedPictures.size })
		assertEquals(ParagraphAlignment.CENTER, cover.alignment)
		assertEquals("", cover.text)
		val title = doc.paragraphs[1]
		assertEquals("Title", title.style)
		assertEquals("Test Project", title.text)
		assertTrue(title.isPageBreak, "The title page should start on a fresh page")
	}

	@Test
	fun `a tall cover is held to the text height`() {
		val parts = unzip(renderCover(mapOf(image(3, "Cover", "png", encoded("png", width = 20, height = 80)))))

		val (_, textHeight) = textArea(parts)
		val drawing = drawings(parts).single()
		assertEquals(textHeight, drawing.cy)
		assertEquals((textHeight / 4.0).roundToLong(), drawing.cx)
	}

	@Test
	fun `a cover the story also shows is packaged once`() {
		val parts = unzip(
			renderCover(mapOf(image(3, "Cover", "png", encoded("png"))), markdown = "![image](hammer://image/3)"),
		)

		assertEquals(listOf("word/media/image1.png"), parts.keys.filter { it.startsWith("word/media/") })
		val drawings = drawings(parts)
		assertEquals(2, drawings.size)
		assertEquals(1, drawings.map { it.embed }.distinct().size)
		assertEquals(listOf("1", "2"), drawings.map { it.docPrId })
	}

	@Test
	fun `a cover that is missing or not a raster leaves the document as it is without one`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		val without = unzip(renderCover(emptyMap(), coverImageId = null))

		for (images in listOf(
			emptyMap(),
			mapOf(image(3, "Cover", "svg", svg.encodeToByteArray())),
			mapOf(image(3, "Cover", "png", "not an image".encodeToByteArray())),
		)) {
			val parts = unzip(renderCover(images))

			assertEquals(without.keys, parts.keys, images.toString())
			without.forEach { (name, bytes) -> assertContentEquals(bytes, parts[name], "$name with $images") }
		}
	}

	private companion object {
		const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
		const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
		const val WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
		const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
		const val PACKAGE_RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
		const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
		const val IMAGE_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
		const val SETTINGS_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings"
		const val FONT_TABLE_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable"
		const val DCTERMS_NS = "http://purl.org/dc/terms/"
		const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
		const val WORD_XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
		const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50

		/** Part way through a second, which the core properties drop. */
		val PINNED_CLOCK = object : Clock {
			override fun now() = Instant.parse("2026-10-31T12:00:00.750Z")
		}

		/** 914400 EMUs per inch over 1440 twips per inch. */
		const val EMU_PER_TWIP = 635L
	}
}
