package components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.components.projecthome.StoryChapter
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStrings
import com.darkrockstudios.apps.hammer.common.components.projecthome.writeStoryAsDocx
import okio.Buffer
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocxStoryRendererTest {

	private fun render(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Test Author"),
	): XWPFDocument {
		val buffer = Buffer()
		val author = projectData.authorName?.takeIf { it.isNotBlank() }
		writeStoryAsDocx(
			sink = buffer,
			projectName = projectName,
			projectData = projectData,
			chapters = chapters,
			strings = ExportStrings(contentsTitle = "Contents", authorByline = author?.let { "by $it" }),
		)
		return XWPFDocument(ByteArrayInputStream(buffer.readByteArray()))
	}

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
			)
		)

		val headings = doc.paragraphs.filter { it.style == "Heading1" }
		assertEquals(listOf("1. Alpha", "2. Beta"), headings.map { it.text })
		assertTrue(headings.all { it.isPageBreak }, "Chapter headings should start on a new page")
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
		assertEquals("1. Q & A <chapter>", heading.text)

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
}
