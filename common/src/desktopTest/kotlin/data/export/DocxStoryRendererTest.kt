package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.writeStoryAsDocx
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
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
		contact: ManuscriptContact = ManuscriptContact(),
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
				wordCount = WORD_COUNT,
				endMarker = "END",
			),
			options = options,
			imageNames = imageNames,
			images = images,
			contact = contact,
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
		contact: ManuscriptContact = ManuscriptContact(),
	): XWPFDocument = XWPFDocument(
		ByteArrayInputStream(renderBytes(chapters, projectName, projectData, options, imageNames, images, contact)),
	)

	private fun XWPFDocument.paragraphTexts(): List<String> = paragraphs.map { it.text }

	/** The paragraphs after the first chapter heading, without the closing END. */
	private fun XWPFDocument.bodyParagraphs(): List<XWPFParagraph> {
		val headingIndex = paragraphs.indexOfFirst { it.style == "Heading1" }
		assertTrue(headingIndex >= 0, "Document should contain a Heading1 chapter heading")
		return paragraphs.drop(headingIndex + 1).dropLast(1)
	}

	private fun XWPFDocument.bodyTexts(): List<String> = bodyParagraphs().map { it.text }

	private fun Any?.twips(): Int? = (this as? BigInteger)?.toInt()

	private fun XWPFParagraph.spaceBefore(): Int? = ctp.pPr?.spacing?.before.twips()
	private fun XWPFParagraph.spaceAfter(): Int? = ctp.pPr?.spacing?.after.twips()
	private fun XWPFParagraph.line(): Int? = ctp.pPr?.spacing?.line.twips()

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
	fun `a novel's title page holds the contact block then the centred title and byline about halfway down`() {
		val doc = render(listOf(StoryChapter("Alpha", "Some text.")), contact = FULL_CONTACT)

		val paragraphs = doc.paragraphs
		assertEquals(
			listOf(
				"Jane Q. Writer\t$WORD_COUNT",
				"12 Main St",
				"Apt 3",
				"Springfield, IL 62701",
				"555-0100",
				"jane@example.com",
				"Test Project",
				"by Test Author",
				"Alpha",
			),
			paragraphs.take(9).map { it.text },
		)
		paragraphs.take(6).forEach { contactLine ->
			assertEquals(240, contactLine.line(), "The contact block is single spaced")
			assertEquals(ParagraphAlignment.LEFT, contactLine.alignment)
		}

		val title = paragraphs[6]
		assertEquals(ParagraphAlignment.CENTER, title.alignment)
		val titleTop = MARGIN + 6 * 276 + assertNotNull(title.spaceBefore())
		assertTrue(titleTop in (PAGE_HEIGHT * 0.4).toInt()..(PAGE_HEIGHT * 0.6).toInt(), "Title at $titleTop twips")

		val byline = paragraphs[7]
		assertEquals(ParagraphAlignment.CENTER, byline.alignment)
		assertNull(byline.spaceBefore(), "The byline sits on the next double-spaced line")

		assertTrue(paragraphs.take(8).none { it.isPageBreak })
		assertTrue(paragraphs[8].isPageBreak, "The text starts on a page after the title page")
	}

	@Test
	fun `the word count sits flush right on the first contact line by a right tab at the text width`() {
		val bytes = renderBytes(listOf(StoryChapter("Alpha", "Text.")), contact = FULL_CONTACT)

		val first = XWPFDocument(ByteArrayInputStream(bytes)).paragraphs.first()
		val tab = first.ctp.pPr.tabs.tabList.single()
		assertEquals("right", tab.`val`.toString())
		assertEquals(textArea(unzip(bytes)).first / EMU_PER_TWIP, tab.pos.twips()!!.toLong())
		assertEquals(listOf("Jane Q. Writer", "\t", WORD_COUNT), first.runs.map { it.text() })
	}

	@Test
	fun `blank contact fields and address lines are left out`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "Text.")),
			contact = ManuscriptContact(legalName = " ", address = "\r\n  \r\nOnly Line\n", phone = "", email = "e@example.com"),
		)

		assertEquals(
			listOf("Only Line\t$WORD_COUNT", "e@example.com", "Test Project"),
			doc.paragraphTexts().take(3),
		)
	}

	@Test
	fun `with no contact the word count still stands flush right on a line of its own`() {
		val doc = render(listOf(StoryChapter("Alpha", "Text.")))

		val first = doc.paragraphs.first()
		assertEquals("\t$WORD_COUNT", first.text)
		assertEquals("right", first.ctp.pPr.tabs.tabList.single().`val`.toString())
		assertEquals("Test Project", doc.paragraphs[1].text)
	}

	@Test
	fun `a short story puts the title on page 1 and starts the text two double spaces below the byline`() {
		val doc = render(
			listOf(StoryChapter("Test Project", listOf("Once upon a time."))),
			options = ExportOptions(manuscriptShortStory = true),
			contact = FULL_CONTACT,
		)

		val paragraphs = doc.paragraphs
		assertEquals(
			listOf("Test Project", "by Test Author", "Once upon a time.", "END"),
			paragraphs.drop(6).map { it.text },
		)
		val title = paragraphs[6]
		assertEquals(ParagraphAlignment.CENTER, title.alignment)
		val titleTop = MARGIN + 6 * 276 + assertNotNull(title.spaceBefore())
		assertTrue(titleTop in (PAGE_HEIGHT * 0.25).toInt()..(PAGE_HEIGHT * 0.5).toInt(), "Title at $titleTop twips")

		val byline = paragraphs[7]
		assertEquals(ParagraphAlignment.CENTER, byline.alignment)
		assertEquals(480, byline.spaceAfter(), "One blank double-spaced line between the byline and the text")
		assertEquals("BodyText", paragraphs[8].style)
		assertTrue(paragraphs.none { it.isPageBreak }, "The short story layout never breaks the page")
	}

	@Test
	fun `a short story without a byline leaves the gap below the title`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "Text.")),
			projectData = ProjectData(authorName = null),
			options = ExportOptions(manuscriptShortStory = true),
		)

		val title = doc.paragraphs[1]
		assertEquals("Test Project", title.text)
		assertEquals(480, title.spaceAfter())
		assertEquals("Text.", doc.paragraphs[2].text)
	}

	@Test
	fun `a single-chapter short story prints no chapter heading`() {
		val doc = render(
			listOf(StoryChapter("Chapter One", "Text.")),
			options = ExportOptions(manuscriptShortStory = true, numberChapters = true),
		)

		assertTrue(doc.paragraphs.none { it.style == "Heading1" })
		assertTrue(doc.paragraphTexts().none { it.contains("Chapter One") })
	}

	@Test
	fun `a short story's chapter headings run on in the flow, a double space below the text before`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "First."), StoryChapter("Beta", "Second.")),
			options = ExportOptions(manuscriptShortStory = true),
		)

		val headings = doc.paragraphs.filter { it.style == "Heading1" }
		assertEquals(listOf("Alpha", "Beta"), headings.map { it.text })
		assertTrue(headings.none { it.isPageBreak })
		assertNull(headings[0].spaceBefore(), "The byline's gap already sets off the first heading")
		assertEquals(480, headings[1].spaceBefore())
	}

	@Test
	fun `a novel's chapters each start a new page about a third of the way down`() {
		val doc = render(listOf(StoryChapter("Alpha", "First."), StoryChapter("Beta", "Second.")))

		val headings = doc.paragraphs.filter { it.style == "Heading1" }
		assertEquals(listOf("Alpha", "Beta"), headings.map { it.text })
		headings.forEach { heading ->
			assertTrue(heading.isPageBreak, "${heading.text} should start a new page")
			val top = MARGIN + assertNotNull(heading.spaceBefore())
			assertTrue(top in (PAGE_HEIGHT * 0.25).toInt()..(PAGE_HEIGHT * 0.4).toInt(), "Heading at $top twips")
			assertEquals(480, heading.spaceAfter(), "A double-spaced line between the heading and the text")
		}
	}

	@Test
	fun `chapter headings are plain centred 12pt text kept with the text and in the outline`() {
		val doc = render(listOf(StoryChapter("Alpha", "Text.")))

		val style = assertNotNull(doc.styles.getStyle("Heading1")).ctStyle
		assertEquals("center", style.pPr.jc.`val`.toString())
		assertEquals(0, style.pPr.outlineLvl.`val`.toInt())
		assertTrue(style.pPr.isSetKeepNext)
		assertTrue(style.rPr == null || (style.rPr.sizeOfBArray() == 0 && style.rPr.sizeOfSzArray() == 0 && style.rPr.sizeOfColorArray() == 0))
	}

	@Test
	fun `numbered chapters prefix their position`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "First."), StoryChapter("Beta", "Second.")),
			options = ExportOptions(numberChapters = true),
		)

		assertEquals(listOf("1. Alpha", "2. Beta"), doc.paragraphs.filter { it.style == "Heading1" }.map { it.text })
	}

	@Test
	fun `chapters are unnumbered by default`() {
		val doc = render(listOf(StoryChapter("Prologue", "First."), StoryChapter("Chapter 1", "Second.")))

		assertEquals(listOf("Prologue", "Chapter 1"), doc.paragraphs.filter { it.style == "Heading1" }.map { it.text })
	}

	@Test
	fun `an empty chapter prints only its heading`() {
		val doc = render(listOf(StoryChapter("Alpha", ""), StoryChapter("Beta", "Text.")))

		assertEquals(listOf("Alpha", "Beta", "Text.", "END"), doc.paragraphTexts().dropWhile { it != "Alpha" })
	}

	/** The running header's text, with the PAGE field's instruction in braces. */
	private fun headerText(parts: Map<String, ByteArray>, name: String): String {
		val header = parseXml(assertNotNull(parts["word/$name"]))
		val out = StringBuilder()
		fun walk(element: Element) {
			when (element.localName) {
				"t" -> out.append(element.textContent)
				"instrText" -> out.append("{${element.textContent.trim()}}")
			}
			(0 until element.childNodes.length).map { element.childNodes.item(it) }.filterIsInstance<Element>().forEach(::walk)
		}
		walk(header.documentElement)
		return out.toString()
	}

	@Test
	fun `the running header reads surname, title and page number flush right`() {
		val parts = unzip(
			renderBytes(listOf(StoryChapter("Alpha", "Text.")), projectData = ProjectData(authorName = "Ann Leckie")),
		)

		assertEquals("Leckie / Test Project / {PAGE}1", headerText(parts, "header1.xml"))
		val jc = parseXml(assertNotNull(parts["word/header1.xml"])).elements(W_NS, "jc").single()
		assertEquals("right", jc.getAttributeNS(W_NS, "val"))
	}

	@Test
	fun `the header surname falls back to the legal name, then is left out`() {
		fun header(author: String?, legalName: String) = headerText(
			unzip(
				renderBytes(
					listOf(StoryChapter("Alpha", "Text.")),
					projectData = ProjectData(authorName = author),
					contact = ManuscriptContact(legalName = legalName),
				),
			),
			"header1.xml",
		)

		assertEquals("Writer / Test Project / {PAGE}1", header(null, "Jane Q. Writer"))
		assertEquals("Writer / Test Project / {PAGE}1", header("  ", "Jane Q. Writer "))
		assertEquals("Pen / Test Project / {PAGE}1", header("Pen", "Jane Q. Writer"))
		assertEquals("Test Project / {PAGE}1", header(null, " "))
	}

	private fun runningHeader(
		projectName: String = "Test Project",
		author: String? = "Test Author",
		legalName: String = "",
	): String = headerText(
		unzip(
			renderBytes(
				listOf(StoryChapter("Alpha", "Text.")),
				projectName = projectName,
				projectData = ProjectData(authorName = author),
				contact = ManuscriptContact(legalName = legalName),
			),
		),
		"header1.xml",
	)

	@Test
	fun `the header surname skips a generational suffix and a trailing comma`() {
		assertEquals("King / Test Project / {PAGE}1", runningHeader(author = "Martin Luther King Jr."))
		assertEquals("King / Test Project / {PAGE}1", runningHeader(author = "Martin Luther King, Jr."))
		assertEquals("King / Test Project / {PAGE}1", runningHeader(author = "Martin Luther King, jr"))
		assertEquals("Davis / Test Project / {PAGE}1", runningHeader(author = "Sammy Davis Sr."))
		assertEquals("Gates / Test Project / {PAGE}1", runningHeader(author = "William Gates III"))
		assertEquals("Smith / Test Project / {PAGE}1", runningHeader(author = "John Smith ii"))
		assertEquals("Writer / Test Project / {PAGE}1", runningHeader(author = "Jane Writer,"))
		assertEquals("Writer / Test Project / {PAGE}1", runningHeader(author = null, legalName = "Jane Q. Writer, IV"))
	}

	@Test
	fun `a short title runs in the header whole, leading article and all`() {
		assertEquals("Author / The Fifth Season / {PAGE}1", runningHeader(projectName = "The Fifth Season"))
		val thirty = "A Title Of Exactly Thirty Char"
		assertEquals(30, thirty.length)
		assertEquals("Author / $thirty / {PAGE}1", runningHeader(projectName = thirty))
	}

	@Test
	fun `a long title is cut to its leading words in the header, without a leading article`() {
		assertEquals(
			"Author / Left Hand of Darkness and / {PAGE}1",
			runningHeader(projectName = "The Left Hand of Darkness and Other Stories"),
		)
		assertEquals(
			"Author / Extraordinarily Long Title for / {PAGE}1",
			runningHeader(projectName = "an Extraordinarily Long Title for a Story"),
		)
		assertEquals(
			"Author / Anthology of Stories Told by / {PAGE}1",
			runningHeader(projectName = "Anthology of Stories Told by the Fire"),
		)
	}

	@Test
	fun `a long title of one long word keeps that word in the header`() {
		assertEquals(
			"Author / Supercalifragilisticexpialidocious / {PAGE}1",
			runningHeader(projectName = "Supercalifragilisticexpialidocious"),
		)
		assertEquals(
			"Author / Supercalifragilisticexpialidocious / {PAGE}1",
			runningHeader(projectName = "The Supercalifragilisticexpialidocious Tale"),
		)
	}

	@Test
	fun `page 1 has an empty header of its own`() {
		val parts = unzip(renderBytes(listOf(StoryChapter("Alpha", "Text."))))

		val sectPr = parseXml(assertNotNull(parts["word/document.xml"])).elements(W_NS, "sectPr").single()
		assertEquals(1, sectPr.getElementsByTagNameNS(W_NS, "titlePg").length)
		val rels = documentRels(parts)
		val targets = sectPr.getElementsByTagNameNS(W_NS, "headerReference").let { refs ->
			List(refs.length) { refs.item(it) as Element }.associate {
				it.getAttributeNS(W_NS, "type") to rels.getValue(it.getAttributeNS(R_NS, "id")).getAttribute("Target")
			}
		}
		assertEquals(mapOf("default" to "header1.xml", "first" to "header2.xml"), targets)
		assertEquals("", headerText(parts, "header2.xml"))

		val overrides = parseXml(assertNotNull(parts["[Content_Types].xml"]))
			.elements(CONTENT_TYPES_NS, "Override")
			.associate { it.getAttribute("PartName") to it.getAttribute("ContentType") }
		for (header in listOf("/word/header1.xml", "/word/header2.xml")) {
			assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml", overrides[header])
		}
	}

	@Test
	fun `a novel numbers its text pages from 1, a short story from its first page`() {
		fun pgNumType(shortStory: Boolean) = parseXml(
			assertNotNull(
				unzip(
					renderBytes(listOf(StoryChapter("Alpha", "Text.")), options = ExportOptions(manuscriptShortStory = shortStory)),
				)["word/document.xml"],
			),
		).elements(W_NS, "pgNumType")

		assertEquals("0", pgNumType(shortStory = false).single().getAttributeNS(W_NS, "start"))
		assertTrue(pgNumType(shortStory = true).isEmpty())
	}

	@Test
	fun `pages are US Letter with one inch margins`() {
		val document = parseXml(assertNotNull(unzip(renderBytes(listOf(StoryChapter("Alpha", "Text."))))["word/document.xml"]))

		val pgSz = document.elements(W_NS, "pgSz").single()
		assertEquals(listOf("12240", "15840"), listOf("w", "h").map { pgSz.getAttributeNS(W_NS, it) })
		val pgMar = document.elements(W_NS, "pgMar").single()
		assertEquals(List(4) { "1440" }, listOf("top", "right", "bottom", "left").map { pgMar.getAttributeNS(W_NS, it) })
	}

	@Test
	fun `body text is double spaced 12pt black with a half-inch first-line indent and no paragraph spacing`() {
		val doc = render(listOf(StoryChapter("Alpha", "First paragraph.\nSecond paragraph.\n\n- list item")))

		val defaults = doc.styles.ctStyles.docDefaults
		val spacing = defaults.pPrDefault.pPr.spacing
		assertEquals(480, spacing.line.twips())
		assertEquals("auto", spacing.lineRule.toString())
		assertEquals(0, spacing.after.twips())
		assertEquals(24, defaults.rPrDefault.rPr.szList.single().`val`.twips())
		assertEquals("auto", defaults.rPrDefault.rPr.colorList.single().`val`.toString())

		val bodyText = assertNotNull(doc.styles.getStyle("BodyText"), "BodyText style must be defined")
		assertEquals(720, bodyText.ctStyle.pPr?.ind?.firstLine.twips())

		val byText = doc.bodyParagraphs().associateBy { it.text }
		assertEquals("BodyText", assertNotNull(byText["First paragraph."]).style)
		assertEquals("BodyText", assertNotNull(byText["Second paragraph."]).style)
		assertNull(assertNotNull(byText["list item"]).style, "List items should not get the indented body style")
		assertTrue(doc.bodyParagraphs().none { it.ctp.pPr?.spacing != null }, "Body paragraphs keep the default spacing")
	}

	@Test
	fun `a centred # marks the break between scenes`() {
		val doc = render(listOf(StoryChapter("Alpha", listOf("One.", "Two.", "Three."))))

		assertEquals(listOf("One.", "#", "Two.", "#", "Three."), doc.bodyTexts())
		doc.bodyParagraphs().filter { it.text == "#" }.forEach { breakLine ->
			assertEquals(ParagraphAlignment.CENTER, breakLine.alignment)
			assertNull(breakLine.style, "A break has no first-line indent")
		}
	}

	@Test
	fun `a markdown rule and a run of blank lines each become a #, a lone blank line nothing`() {
		val doc = render(listOf(StoryChapter("Alpha", "One.\n\n---\n\nTwo.\n\n\nThree.\n\nFour.")))

		assertEquals(listOf("One.", "#", "Two.", "#", "Three.", "Four."), doc.bodyTexts())
	}

	@Test
	fun `consecutive breaks collapse to one`() {
		val doc = render(listOf(StoryChapter("Alpha", listOf("One.\n\n---", "\n\n\n---\n\n\nTwo."))))

		assertEquals(listOf("One.", "#", "Two."), doc.bodyTexts())
	}

	@Test
	fun `no break falls at a chapter's edges or around empty scenes`() {
		val doc = render(
			listOf(
				StoryChapter("Alpha", listOf("", "---\n\nOne.", "", "\n\n\n", "Two.\n\n---\n\n\n")),
				StoryChapter("Beta", listOf("\n\n\nThree.")),
			),
		)

		assertEquals(listOf("Alpha", "One.", "#", "Two.", "Beta", "Three.", "END"), doc.paragraphTexts().dropWhile { it != "Alpha" })
	}

	@Test
	fun `the chapter separator does not reach the docx`() {
		val doc = render(listOf(StoryChapter("Alpha", listOf("One.", "Two."), separator = "\n\nSEPARATOR\n\n")))

		assertEquals(listOf("One.", "#", "Two."), doc.bodyTexts())
	}

	@Test
	fun `END closes the manuscript, centred`() {
		for (shortStory in listOf(false, true)) {
			val doc = render(
				listOf(StoryChapter("Alpha", "One."), StoryChapter("Beta", "Two.")),
				options = ExportOptions(manuscriptShortStory = shortStory),
			)

			val end = doc.paragraphs.last()
			assertEquals("END", end.text)
			assertEquals(ParagraphAlignment.CENTER, end.alignment)
			assertEquals("Two.", doc.paragraphs[doc.paragraphs.size - 2].text)
		}
	}

	@Test
	fun `the modern manuscript is Times New Roman whatever the font option says`() {
		val bytes = renderBytes(listOf(StoryChapter("Alpha", "Text.")), options = ExportOptions(font = ExportFont.Arial))
		val parts = unzip(bytes)

		val fonts = XWPFDocument(ByteArrayInputStream(bytes)).styles.ctStyles.docDefaults.rPrDefault.rPr.rFontsList.single()
		assertEquals("Times New Roman", fonts.ascii)
		assertEquals("Times New Roman", fonts.hAnsi)
		val fontTable = parseXml(assertNotNull(parts["word/fontTable.xml"])).elements(W_NS, "font")
		assertEquals(listOf("Times New Roman", "Courier New"), fontTable.map { it.getAttributeNS(W_NS, "name") })
	}

	@Test
	fun `the classic manuscript is Courier New`() {
		val bytes = renderBytes(listOf(StoryChapter("Alpha", "Text.")), options = ExportOptions(manuscriptClassic = true))

		val fonts = XWPFDocument(ByteArrayInputStream(bytes)).styles.ctStyles.docDefaults.rPrDefault.rPr.rFontsList.single()
		assertEquals("Courier New", fonts.ascii)
		assertEquals("Courier New", fonts.hAnsi)
		val fontTable = parseXml(assertNotNull(unzip(bytes)["word/fontTable.xml"])).elements(W_NS, "font").single()
		assertEquals("Courier New", fontTable.getAttributeNS(W_NS, "name"))
		assertEquals("modern", fontTable.getElementsByTagNameNS(W_NS, "family").let { (it.item(0) as Element).getAttributeNS(W_NS, "val") })
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
	fun `markdown headings in a scene sit below the chapter level in bold`() {
		val doc =
			render(listOf(StoryChapter("Alpha", "# Top\n\n## Section\n\nBody text.\n\n### Subsection")))

		val styles = doc.bodyParagraphs().associate { it.text to it.style }
		assertEquals("Heading2", styles["Top"])
		assertEquals("Heading2", styles["Section"])
		assertEquals("Heading3", styles["Subsection"])

		val heading2 = assertNotNull(doc.styles.getStyle("Heading2")).ctStyle
		assertEquals(1, heading2.rPr.sizeOfBArray())
		assertEquals(0, heading2.rPr.sizeOfColorArray())
		assertEquals(0, heading2.rPr.sizeOfSzArray())
		assertNull(heading2.pPr.jc, "Scene headings are left aligned")
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
	fun `blockquotes are set off by an indent on both sides, not italics`() {
		val doc = render(listOf(StoryChapter("Alpha", "> quoted wisdom")))

		val quote = doc.bodyParagraphs().first { it.text == "quoted wisdom" }
		assertEquals("Quote", quote.style)
		val style = assertNotNull(doc.styles.getStyle("Quote")).ctStyle
		assertEquals(720, style.pPr.ind.left.twips())
		assertEquals(720, style.pPr.ind.right.twips())
		assertTrue(style.rPr == null || style.rPr.sizeOfIArray() == 0)
	}

	@Test
	fun `inline code and code blocks use Courier New`() {
		val markdown = "Call `doThing()` now.\n\n```\nval x = 1\n```"
		val doc = render(listOf(StoryChapter("Alpha", markdown)))

		val runs = doc.bodyParagraphs().flatMap { it.runs }
		assertEquals("Courier New", runs.first { it.text() == "doThing()" }.fontFamily)

		val block = doc.bodyParagraphs().first { it.text == "val x = 1" }
		assertEquals("Courier New", block.runs.single().fontFamily)
		assertNull(block.style, "A code line has no first-line indent")
	}

	@Test
	fun `links are written as plain text`() {
		val bytes = renderBytes(listOf(StoryChapter("Alpha", "Visit [the site](https://example.com/page) today.")))
		val parts = unzip(bytes)

		val doc = XWPFDocument(ByteArrayInputStream(bytes))
		assertEquals(listOf("Visit the site today."), doc.bodyTexts())
		assertTrue(doc.bodyParagraphs().flatMap { it.runs }.none { it is XWPFHyperlinkRun })
		assertTrue(documentRels(parts).values.none { it.getAttribute("TargetMode") == "External" })
		assertNull(doc.styles.getStyle("Hyperlink"))
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
			projectData = ProjectData(authorName = "Ann <Author> & Co"),
		)

		assertEquals("Tom & Jerry's <Story>", doc.properties.coreProperties.title)
		assertTrue(doc.paragraphTexts().contains("Tom & Jerry's <Story>"))

		val heading = doc.paragraphs.first { it.style == "Heading1" }
		assertEquals("Q & A <chapter>", heading.text)

		val texts = doc.paragraphTexts()
		assertTrue(
			texts.any { it.contains("Ampersands & angles <like> \"these\" 'ones'.") },
			"Body text with XML metacharacters should round-trip, got $texts",
		)
	}

	@Test
	fun `no author and no chapters still produces a valid document`() {
		val doc = render(
			chapters = emptyList(),
			projectData = ProjectData(authorName = null),
		)

		assertEquals("Test Project", doc.properties.coreProperties.title)
		assertEquals("Test Project", doc.paragraphs[1].text)
		assertTrue(
			doc.paragraphTexts().none { it.startsWith("by ") },
			"No byline expected without an author"
		)
		assertEquals("END", doc.paragraphs.last().text)
	}

	@Test
	fun `a novel with no chapters prints its title page, then END on a page of its own`() {
		val doc = render(chapters = emptyList())

		assertEquals(listOf("\t$WORD_COUNT", "Test Project", "by Test Author", "END"), doc.paragraphTexts())
		assertTrue(doc.paragraphs.none { it.style == "Heading1" })
		val end = doc.paragraphs.last()
		assertTrue(end.isPageBreak, "END should start a new page after the title page")
		assertEquals(ParagraphAlignment.CENTER, end.alignment)
	}

	@Test
	fun `a short story with no chapters prints its first page, then END in the flow`() {
		val doc = render(chapters = emptyList(), options = ExportOptions(manuscriptShortStory = true))

		assertEquals(listOf("\t$WORD_COUNT", "Test Project", "by Test Author", "END"), doc.paragraphTexts())
		assertTrue(doc.paragraphs.none { it.style == "Heading1" })
		assertFalse(doc.paragraphs.last().isPageBreak, "END should run on below the byline")
	}

	@Test
	fun `every authored line becomes its own paragraph`() {
		val doc = render(listOf(StoryChapter("Alpha", "line one\nline two")))

		assertEquals(listOf("line one", "line two"), doc.bodyTexts())
	}

	@Test
	fun `a project image is written as its current name, or not at all without one`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "![image](hammer://image/3)\n\n![Old](hammer://image/9)")),
			imageNames = mapOf(3 to "Cheshire Cat"),
		)

		assertEquals(listOf("Cheshire Cat"), doc.bodyTexts().filter { it.isNotBlank() })
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
	fun `an embedded image sits centred and single spaced in place of its name`() {
		val doc = render(
			listOf(StoryChapter("Alpha", "Before.\n\n![image](hammer://image/3)\n\nAfter.")),
			imageNames = mapOf(3 to "Cat"),
			images = mapOf(image(3, "Cat", "png", encoded("png"))),
		)

		val body = doc.bodyParagraphs()
		assertTrue(body.none { it.text == "Cat" }, "The name should not be written")
		val picture = body.single { paragraph -> paragraph.runs.any { it.embeddedPictures.isNotEmpty() } }
		assertEquals(ParagraphAlignment.CENTER, picture.alignment)
		assertEquals(240, picture.line())
		assertNull(picture.style, "A picture has no first-line indent")
		assertEquals(1, doc.allPictures.size)
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
		val texts = XWPFDocument(ByteArrayInputStream(bytes)).bodyTexts()
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
	fun `the project cover is not printed`() {
		val images = mapOf(image(3, "Cover", "png", encoded("png")))
		val parts = unzip(
			renderBytes(
				listOf(StoryChapter("Alpha", "Body.")),
				projectData = ProjectData(authorName = "Test Author", coverImageId = 3),
				imageNames = images.mapValues { it.value.name },
				images = images,
			),
		)

		assertTrue(parts.keys.none { it.startsWith("word/media/") })
		assertTrue(drawings(parts).isEmpty())
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
	fun `images take the relationship ids after the fixed parts`() {
		val parts = renderImages(
			listOf(3, 4),
			mapOf(image(3, "Cat", "png", encoded("png")), image(4, "Dog", "jpg", encoded("jpg"))),
		)

		val rels = documentRels(parts)
		assertEquals((1..8).map { "rId$it" }, rels.keys.toList())
		assertEquals(
			listOf("styles.xml", "numbering.xml", "settings.xml", "fontTable.xml", "header1.xml", "header2.xml"),
			(1..6).map { rels.getValue("rId$it").getAttribute("Target") },
		)
		assertEquals("media/image1.png", rels.getValue("rId7").getAttribute("Target"))
		assertEquals("media/image2.jpeg", rels.getValue("rId8").getAttribute("Target"))
		assertEquals(listOf("rId7", "rId8"), drawings(parts).map { it.embed })
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
		assertEquals(12, xmlParts.size, "Expected every package part, got ${parts.keys}")
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
		val parts = unzip(renderBytes(listOf(StoryChapter("Alpha", "Text."))))

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

		const val WORD_COUNT = "about 12,500 words"

		/** US Letter height and its margin, in twips. */
		const val PAGE_HEIGHT = 15840
		const val MARGIN = 1440

		val FULL_CONTACT = ManuscriptContact(
			legalName = "Jane Q. Writer",
			address = "12 Main St\nApt 3\nSpringfield, IL 62701",
			phone = "555-0100",
			email = "jane@example.com",
		)

		/** Part way through a second, which the core properties drop. */
		val PINNED_CLOCK = object : Clock {
			override fun now() = Instant.parse("2026-10-31T12:00:00.750Z")
		}

		/** 914400 EMUs per inch over 1440 twips per inch. */
		const val EMU_PER_TWIP = 635L
	}
}
