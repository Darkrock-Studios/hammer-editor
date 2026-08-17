package components.projecthome

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStrings
import com.darkrockstudios.apps.hammer.common.components.projecthome.StoryChapter
import com.darkrockstudios.apps.hammer.common.components.projecthome.writeStoryAsEpub
import okio.Buffer
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class EpubStoryRendererTest {

	/** Renders the EPUB and concatenates every text part (xhtml / opf / ncx / css) for content assertions. */
	private fun render(
		chapters: List<StoryChapter>,
		projectName: String = "Test Project",
		projectData: ProjectData = ProjectData(authorName = "Jane Doe"),
	): String {
		val buffer = Buffer()
		val author = projectData.authorName?.takeIf { it.isNotBlank() }
		writeStoryAsEpub(
			sink = buffer,
			projectName = projectName,
			projectData = projectData,
			chapters = chapters,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = author?.let { "by $it" }),
		)

		val sb = StringBuilder()
		ZipInputStream(ByteArrayInputStream(buffer.readByteArray())).use { zis ->
			var entry = zis.nextEntry
			while (entry != null) {
				if (!entry.isDirectory) sb.append(zis.readBytes().decodeToString()).append('\n')
				entry = zis.nextEntry
			}
		}
		return sb.toString()
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
}
