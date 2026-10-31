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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
		imageNames: Map<Int, String> = emptyMap(),
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
			imageNames = imageNames,
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
		imageNames: Map<Int, String> = emptyMap(),
	): String = renderEntries(chapters, projectName, projectData, options, images, imageNames).values
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
		assertTrue("""<img src="images/3.png" alt="cat"/>""" in chapter, chapter)
		assertTrue("hammer://image" !in chapter, chapter)

		val css = entries.filterKeys { it.endsWith(".css") }.values.joinToString { it.decodeToString() }
		assertTrue("img { display: block; max-width: 100%; height: auto; margin: 1em auto; }" in css, css)
	}

	@Test
	fun `special characters in the image name are escaped in the img tag`() {
		val chapter = renderEntries(
			listOf(StoryChapter("Alpha", "![image](hammer://image/3)")),
			images = mapOf(3 to ExportImage(id = 3, name = """Tom & "Jerry" <3""", fileExtension = "png", bytes = PNG_BYTES)),
		).text("OEBPS/ch1.xhtml")

		assertTrue("""alt="Tom &amp; &quot;Jerry&quot; &lt;3"/>""" in chapter, chapter)
	}

	@Test
	fun `a packaged image is described by its name whatever its alt text`() {
		val chapter = renderEntries(
			listOf(StoryChapter("Alpha", "![](hammer://image/3)\n\n![image](hammer://image/3)")),
			images = mapOf(3 to catImage),
		).text("OEBPS/ch1.xhtml")

		assertEquals(2, Regex("""<img src="images/3\.png" alt="cat"/>""").findAll(chapter).count(), chapter)
	}

	@Test
	fun `an image with an extension the book cannot carry is written as its name`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "![A cat](hammer://image/3)")),
			images = mapOf(3 to ExportImage(id = 3, name = "Tom & <Jerry>", fileExtension = "bmp", bytes = PNG_BYTES)),
			imageNames = mapOf(3 to "Tom & <Jerry>"),
		)

		val chapter = entries.text("OEBPS/ch1.xhtml")
		assertTrue("<p>Tom &amp; &lt;Jerry&gt;</p>" in chapter, chapter)
		assertTrue("A cat" !in chapter, chapter)
		assertTrue("<img" !in chapter, chapter)
		assertTrue(entries.keys.none { it.startsWith("OEBPS/images/") }, entries.keys.toString())
	}

	@Test
	fun `an image that is not packaged is written as its name, or as nothing without one`() {
		val content = renderEntries(
			listOf(StoryChapter("Alpha", "Before.\n\n![A *black* cat](hammer://image/3)\n\n![Old](hammer://image/5)")),
			images = mapOf(4 to ExportImage(id = 4, name = "dog", fileExtension = "png", bytes = PNG_BYTES)),
			imageNames = mapOf(3 to "cat"),
		).text("OEBPS/ch1.xhtml")

		assertTrue("<p>cat</p>" in content, content)
		assertTrue("black" !in content, content)
		assertTrue("Old" !in content, content)
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

	private fun withoutBookId(ncx: String): String = ncx.replace(Regex("""name="dtb:UUID" content="[^"]*""""), "")

	private fun spineIdrefs(opf: String): List<String> =
		Regex("""<(?:opf:)?itemref [^>]*idref="([^"]+)"""").findAll(opf).map { it.groupValues[1] }.toList()

	@Test
	fun `a cover that decodes opens the book on a cover page outside the table of contents`() {
		val cover = ExportImage(id = 3, name = "cat", fileExtension = "png", bytes = encoded("png", width = 40, height = 20))
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = 3),
			images = mapOf(3 to cover),
		)

		val opf = entries.text("OEBPS/content.opf")
		assertEquals(listOf("cover", "title", "toc", "ch1"), spineIdrefs(opf))
		assertTrue(Regex("""<(?:opf:)?meta [^>]*name="cover"[^>]*content="img3"""").containsMatchIn(opf), opf)
		assertTrue(Regex("""<(?:opf:)?reference [^>]*type="cover"[^>]*href="cover\.xhtml"""").containsMatchIn(opf), opf)

		val page = entries.text("OEBPS/cover.xhtml")
		assertTrue("""xlink:href="images/3.png"""" in page, page)
		assertTrue("""viewBox="0 0 40 20"""" in page, page)
		assertTrue("""preserveAspectRatio="xMidYMid meet"""" in page, page)

		val ncx = entries.text("OEBPS/toc.ncx")
		assertTrue("cover.xhtml" !in ncx, ncx)
		assertTrue("cover.xhtml" !in entries.text("OEBPS/toc.xhtml"))
	}

	@Test
	fun `the cover page parses as xml`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectName = "Tom & <Jerry>",
			projectData = ProjectData(coverImageId = 3),
			images = mapOf(3 to ExportImage(id = 3, name = "cat", fileExtension = "jpg", bytes = encoded("jpg"))),
		)

		val document = parseXhtml(assertNotNull(entries["OEBPS/cover.xhtml"]))
		val image = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "image").item(0) as Element
		assertEquals("images/3.jpg", image.getAttributeNS("http://www.w3.org/1999/xlink", "href"))
	}

	@Test
	fun `a cover that is missing or not a raster leaves the book as it is without one`() {
		val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10"><rect width="20" height="10"/></svg>"""
		fun book(coverImageId: Int?, images: Map<Int, ExportImage>) = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = coverImageId),
			images = images,
		)

		for (images in listOf(
			emptyMap(),
			mapOf(3 to ExportImage(id = 3, name = "cat", fileExtension = "svg", bytes = svg.encodeToByteArray())),
			mapOf(3 to catImage),
		)) {
			val entries = book(coverImageId = 3, images)
			val without = book(coverImageId = null, images)

			assertTrue("OEBPS/cover.xhtml" !in entries, entries.keys.toString())
			assertEquals(listOf("title", "toc", "ch1"), spineIdrefs(entries.text("OEBPS/content.opf")))
			// The package and the NCX carry a fresh book id each time.
			assertEquals(without.keys, entries.keys)
			without.keys.filter { it.endsWith(".xhtml") || it.endsWith(".css") }.forEach { name ->
				assertContentEquals(without[name], entries[name], "$name with $images")
			}
			assertEquals(withoutBookId(without.text("OEBPS/toc.ncx")), withoutBookId(entries.text("OEBPS/toc.ncx")))
		}
	}

	@Test
	fun `every content document declares the XHTML doctype and parses`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Body *one*."), StoryChapter("Beta", "Body two.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = 3),
			images = mapOf(3 to ExportImage(id = 3, name = "cat", fileExtension = "png", bytes = encoded("png"))),
		)

		val pages = entries.keys.filter { it.endsWith(".xhtml") }
		assertEquals(
			listOf("cover", "title", "toc", "ch1", "ch2").map { "OEBPS/$it.xhtml" }.toSet(),
			pages.toSet(),
		)
		pages.forEach { name ->
			val lines = entries.text(name).lines()
			assertEquals("""<?xml version="1.0" encoding="UTF-8"?>""", lines[0], name)
			assertEquals(XHTML11_DOCTYPE, lines[1], name)
			val root = parseXhtml(assertNotNull(entries[name])).documentElement
			assertEquals("http://www.w3.org/1999/xhtml", root.namespaceURI, name)
			assertEquals("html", root.localName, name)
		}
		val toc = entries.text("OEBPS/toc.xhtml")
		assertTrue("<nav" !in toc && """<div class="toc">""" in toc, toc)
	}

	@Test
	fun `the package document writes its elements in the default namespace`() {
		val entries = renderEntries(
			listOf(StoryChapter("Alpha", "Body.")),
			projectData = ProjectData(authorName = "Jane Doe", coverImageId = 3),
			images = mapOf(3 to ExportImage(id = 3, name = "cat", fileExtension = "png", bytes = encoded("png"))),
		)
		val opf = entries.text("OEBPS/content.opf")

		listOf("<manifest>", "<item ", "<spine", "<itemref ", "<guide>", "<reference ").forEach { tag ->
			assertTrue(tag in opf, "$tag missing from\n$opf")
		}
		assertTrue("<opf:" !in opf, opf)
		val ncxItem = assertNotNull(Regex("""<item [^>]*href="toc\.ncx"[^>]*>""").find(opf), opf).value
		assertTrue("media-type=\"application/x-dtbncx+xml\"" in ncxItem, ncxItem)

		listOf(opf, entries.text("OEBPS/toc.ncx")).forEach { document ->
			val generator = assertNotNull(
				Regex("""name="(?:dtb:)?generator" content="([^"]*)"""").find(document),
				document,
			).groupValues[1]
			assertTrue(generator.startsWith("EPUB4KMP v") && generator != "EPUB4KMP v", generator)
		}
	}

	@Test
	fun `an image in a line of prose leaves its alt text in place`() {
		val content = render(listOf(StoryChapter("Alpha", "See ![the map](https://example.com/map.png) here.")))

		assertTrue("<p>See the map here.</p>" in content, content)
		assertTrue("<img" !in content, content)
	}

	/** Parses a content document without fetching the external DTD its DOCTYPE names. */
	private fun parseXhtml(bytes: ByteArray): Document =
		DocumentBuilderFactory.newInstance().apply {
			isNamespaceAware = true
			isValidating = false
			setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		}
			.newDocumentBuilder()
			.apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
			.parse(ByteArrayInputStream(bytes))

	private val catImage = ExportImage(id = 3, name = "cat", fileExtension = "png", bytes = PNG_BYTES)

	private companion object {
		const val XHTML11_DOCTYPE =
			"""<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">"""
		val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
	}
}
