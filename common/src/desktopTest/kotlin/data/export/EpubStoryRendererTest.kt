package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.writeStoryAsEpub
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import okio.Buffer
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EpubStoryRendererTest {

	/** Renders the EPUB and returns every zip entry's bytes by entry name, in archive order. */
	private fun renderEntries(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Jane Doe"),
		options: ExportOptions = ExportOptions(),
		images: Map<Int, ExportImage> = emptyMap(),
	): Map<String, ByteArray> {
		val buffer = Buffer()
		val author = projectData.authorName?.takeIf { it.isNotBlank() }
		writeStoryAsEpub(
			sink = buffer,
			projectName = projectName,
			projectData = projectData,
			chapters = chapters,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = author?.let { "by $it" }),
			options = options,
			images = images,
		)

		val entries = linkedMapOf<String, ByteArray>()
		ZipInputStream(ByteArrayInputStream(buffer.readByteArray())).use { zis ->
			var entry = zis.nextEntry
			while (entry != null) {
				if (!entry.isDirectory) entries[entry.name] = zis.readBytes()
				entry = zis.nextEntry
			}
		}
		return entries
	}

	/** Renders the EPUB and concatenates every text part (xhtml / opf / ncx / css) for content assertions. */
	private fun render(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Jane Doe"),
		options: ExportOptions = ExportOptions(),
		images: Map<Int, ExportImage> = emptyMap(),
	): String = renderEntries(chapters, projectName, projectData, options, images).values
		.joinToString(separator = "\n", postfix = "\n") { it.decodeToString() }

	private fun Map<String, ByteArray>.text(name: String): String =
		assertNotNull(this[name], "No zip entry $name in ${keys}").decodeToString()

	@Test
	fun `blank lines between passages can be skipped`() {
		val content = render(
			listOf(StoryChapter("Alpha", "First passage.\n\nSecond passage.")),
			options = ExportOptions(keepBlankLines = false),
		)

		assertTrue("<p>First passage.</p><p>Second passage.</p>" in content, content)
	}

	@Test
	fun `a run of blank lines leaves one break when blank lines are skipped`() {
		val content = render(
			listOf(StoryChapter("Alpha", "First passage.\n\n\n\nNew scene.")),
			options = ExportOptions(keepBlankLines = false),
		)

		assertTrue("<p>First passage.</p><br /><p>New scene.</p>" in content, content)
	}

	@Test
	fun `chapter titles are numbered only on request`() {
		val chapters = listOf(StoryChapter("Prologue", "First."), StoryChapter("Chapter 1", "Second."))

		val plain = render(chapters)
		assertTrue("<h1 class=\"chapter-title\">Chapter 1</h1>" in plain, plain)
		assertTrue("2. Chapter 1" !in plain, plain)

		val numbered = render(chapters, options = ExportOptions(numberChapters = true))
		assertTrue("<h1 class=\"chapter-title\">2. Chapter 1</h1>" in numbered, numbered)
		assertTrue("<a href=\"ch2.xhtml\">2. Chapter 1</a>" in numbered, numbered)
	}

	@Test
	fun `the chosen font overrides the stylesheet body font`() {
		val content = render(
			listOf(StoryChapter("Alpha", "Some text.")),
			options = ExportOptions(font = ExportFont.Arial),
		)

		assertTrue("body { font-family: Arial, Helvetica, sans-serif; }" in content, content)
	}

	@Test
	fun `every authored line becomes its own paragraph`() {
		val content = render(listOf(StoryChapter("Alpha", "line one\nline two")))

		assertTrue("<p>line one</p><p>line two</p>" in content, "Authored lines should each stand alone")
	}

	@Test
	fun `a blank line between passages survives as a break`() {
		val content = render(listOf(StoryChapter("Alpha", "First passage.\n\nSecond passage.")))

		assertTrue(
			"<p>First passage.</p><br /><p>Second passage.</p>" in content,
			"The author's blank line should reach the reader",
		)
	}

	@Test
	fun `paragraphs carry no margin so the indent parts the lines`() {
		val content = render(listOf(StoryChapter("Alpha", "Body.")))

		assertTrue("p { margin: 0; }" in content, "Prose lines must not be gapped by a paragraph margin")
	}

	@Test
	fun `contents page carries the localized title`() {
		val content = render(listOf(StoryChapter("Alpha", "Body.")))
		assertTrue("Contents" in content, "The TOC title should appear in the EPUB")
	}

	@Test
	fun `chapters appear with their names and body text`() {
		val content = render(
			listOf(
				StoryChapter("Alpha", "First chapter body."),
				StoryChapter("Beta", "Second chapter body."),
			)
		)
		assertTrue("Alpha" in content, "First chapter name should appear")
		assertTrue("Beta" in content, "Second chapter name should appear")
		assertTrue("First chapter body." in content, "First chapter body should appear")
		assertTrue("Second chapter body." in content, "Second chapter body should appear")
	}

	@Test
	fun `author appears in the document`() {
		val content = render(listOf(StoryChapter("Alpha", "Body.")))
		assertTrue("Jane Doe" in content, "The author should appear in the EPUB")
	}

	@Test
	fun `markdown emphasis becomes HTML strong and em`() {
		val content = render(listOf(StoryChapter("Alpha", "A **bold** and _italic_ word.")))
		assertTrue("<strong>bold</strong>" in content, "Strong markdown should become <strong>")
		assertTrue("<em>italic</em>" in content, "Emphasis markdown should become <em>")
	}

	@Test
	fun `markdown strikethrough renders struck-through text`() {
		val content = render(listOf(StoryChapter("Alpha", "This is ~~gone~~ now.")))

		assertTrue(
			"""<span class="user-del">gone</span>""" in content,
			"GFM strikethrough should become a user-del span",
		)
		assertTrue(
			".user-del { text-decoration: line-through; }" in content,
			"The stylesheet must strike the user-del span through",
		)
	}

	@Test
	fun `theme accent color reaches the stylesheet`() {
		val content = render(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(
				authorName = "Jane",
				theme = ProjectTheme(primary = "#FF112233", secondary = "#FFAABBCC"),
			),
		)
		assertTrue("112233" in content, "The theme primary color should reach the stylesheet")
	}

	@Test
	fun `a packaged image is embedded in the book`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Before.\n\n![A *black* cat](hammer://image/3)\n\nAfter.")),
			images = mapOf(3 to catImage),
		)

		assertContentEquals(catImage.bytes, entries["OEBPS/images/3.png"], "The image bytes should be packaged as is")

		val opf = entries.text("OEBPS/content.opf")
		val item = assertNotNull(Regex("""<(?:opf:)?item [^>]*href="images/3\.png"[^>]*>""").find(opf), opf).value
		assertTrue("media-type=\"image/png\"" in item, item)

		val chapter = entries.text("OEBPS/ch1.xhtml")
		assertTrue("""<img src="images/3.png" alt="A black cat"/>""" in chapter, chapter)
		assertTrue("hammer://image" !in chapter, chapter)

		val css = entries.filterKeys { it.endsWith(".css") }.values.joinToString { it.decodeToString() }
		assertTrue("img { display: block; max-width: 100%; height: auto; margin: 1em auto; }" in css, css)
	}

	@Test
	fun `alt text special characters are escaped in the img tag`() {
		val chapter = renderEntries(
			listOf(StoryChapter("Alpha", """![Tom & "Jerry" <3](hammer://image/3)""")),
			images = mapOf(3 to catImage),
		).text("OEBPS/ch1.xhtml")

		assertTrue("""alt="Tom &amp; &quot;Jerry&quot; &lt;3"/>""" in chapter, chapter)
	}

	@Test
	fun `an image that is not packaged is written as its alt text`() {
		val content = render(
			listOf(StoryChapter("Alpha", "Before.\n\n![A *black* cat](hammer://image/3)\n\nAfter.")),
			images = mapOf(4 to ExportImage(id = 4, name = "dog", fileExtension = "png", bytes = PNG_BYTES)),
		)

		assertTrue("<p>A <em>black</em> cat</p>" in content, content)
		assertTrue("<img" !in content, content)
		assertTrue("hammer://image" !in content, content)
	}

	@Test
	fun `a packaged cover image becomes the book cover`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = 3),
			images = mapOf(3 to catImage),
		)

		val opf = entries.text("OEBPS/content.opf")
		assertTrue(Regex("""<(?:opf:)?meta [^>]*name="cover"[^>]*content="img3"""").containsMatchIn(opf), opf)
		assertContentEquals(catImage.bytes, entries["OEBPS/images/3.png"])
		assertTrue("OEBPS/title.xhtml" in entries, "The title page should stay")
	}

	@Test
	fun `a cover image that is not packaged leaves the book without a cover`() {
		val opf = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = 3),
		).text("OEBPS/content.opf")

		assertTrue("name=\"cover\"" !in opf, opf)
	}

	@Test
	fun `an image in a line of prose leaves its alt text in place`() {
		val content = render(listOf(StoryChapter("Alpha", "See ![the map](https://example.com/map.png) here.")))

		assertTrue("<p>See the map here.</p>" in content, content)
		assertTrue("<img" !in content, content)
	}

	private val catImage = ExportImage(id = 3, name = "cat", fileExtension = "png", bytes = PNG_BYTES)

	private companion object {
		val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
	}
}
