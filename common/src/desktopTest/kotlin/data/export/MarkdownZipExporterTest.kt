package data.export

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.export.exportFileName
import okio.Buffer
import org.junit.jupiter.api.Test
import utils.unzip
import java.net.URI
import java.nio.charset.Charset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How the document opens when its one chapter is named "One". */
private const val STORY_ONE = "# Story\n\n\n## One\n\n"

class MarkdownZipExporterTest {

	private val exporter = StoryExporterRegistry(emptyList()).forFormat(BuiltInExportFormat.MARKDOWN)

	private fun image(id: Int, name: String, extension: String = "png", bytes: ByteArray = byteArrayOf(id.toByte())) =
		id to ExportImage(id, name, extension, bytes)

	private fun ref(id: Int) = "![image](hammer://image/$id)"

	private fun render(
		chapters: List<StoryChapter>,
		images: Map<Int, ExportImage> = emptyMap(),
		imageNames: Map<Int, String> = images.mapValues { it.value.name },
		coverImageId: Int? = null,
		projectName: String = "Story",
		treatTopLevelAsChapters: Boolean = true,
		charset: Charset = Charsets.UTF_8,
	): Map<String, ByteArray> {
		val input = ExportInput(
			projectName = projectName,
			projectData = ProjectData(coverImageId = coverImageId),
			chapters = chapters,
			treatTopLevelAsChapters = treatTopLevelAsChapters,
			options = ExportOptions(format = BuiltInExportFormat.MARKDOWN),
			language = "en",
			strings = ExportStrings(
				contentsTitle = "Contents",
				authorByline = null,
				wordCount = { "about $it words" },
				endMarker = "END",
			),
			images = images,
			imageNames = imageNames,
		)
		val buffer = Buffer()
		exporter.render(buffer, input)
		return unzip(buffer.readByteArray(), charset)
	}

	/** Renders a story of one chapter, "One", holding [markdown]. */
	private fun render(
		markdown: String,
		images: Map<Int, ExportImage> = emptyMap(),
		imageNames: Map<Int, String> = images.mapValues { it.value.name },
		coverImageId: Int? = null,
		projectName: String = "Story",
		charset: Charset = Charsets.UTF_8,
	): Map<String, ByteArray> = render(
		listOf(StoryChapter("One", markdown)),
		images,
		imageNames,
		coverImageId,
		projectName,
		charset = charset,
	)

	private fun Map<String, ByteArray>.markdown(name: String = "Story.md"): String =
		assertNotNull(this[name], "No $name in ${keys}").decodeToString()

	@Test
	fun `the format is a zip that needs project data and embeds images and the cover`() {
		assertEquals("zip", exporter.fileExtension)
		assertEquals("application/zip", exporter.mimeType)
		assertTrue(exporter.needsProjectData)
		assertTrue(exporter.embedsImages)
		assertTrue(exporter.embedsCover)
	}

	@Test
	fun `the zip holds the document and every image it shows, cover first, and nothing else`() {
		val images = mapOf(
			image(1, "Cat"),
			image(2, "Dog", "jpg"),
			image(5, "Cover", "webp"),
			// Loaded for a reference inside fenced code, which stays as written.
			image(9, "Code"),
		)
		val markdown = "${ref(1)}\n\nA ${ref(2)} here.\n\n```\n${ref(9)}\n```"

		val entries = render(markdown, images, coverImageId = 5)

		assertEquals(
			listOf("Story.md", "images/5~Cover.webp", "images/1~Cat.png", "images/2~Dog.jpg"),
			entries.keys.toList(),
		)
		assertTrue("```\n${ref(9)}\n```" in entries.markdown())
	}

	@Test
	fun `image bytes are written unchanged`() {
		val photo = ByteArray(4096) { (it * 31).toByte() }
		val cover = ByteArray(512) { (it * 7).toByte() }
		val images = mapOf(image(1, "Photo", "jpg", photo), image(5, "Cover", "png", cover))

		val entries = render(ref(1), images, coverImageId = 5)

		assertContentEquals(photo, entries["images/1~Photo.jpg"])
		assertContentEquals(cover, entries["images/5~Cover.png"])
	}

	@Test
	fun `a reference on its own line and one inside prose point at the image with its name as alt text`() {
		val images = mapOf(image(1, "Cat"), image(2, "Dog"))
		val markdown = "Before.\n\n${ref(1)}\n\nA ${ref(2)} sat.\n\nAfter."

		val text = render(markdown, images).markdown()

		assertEquals(
			"${STORY_ONE}Before.\n\n![Cat](images/1~Cat.png)\n\nA ![Dog](images/2~Dog.png) sat.\n\nAfter.\n",
			text,
		)
	}

	@Test
	fun `the cover is the first line, above the title`() {
		val images = mapOf(image(5, "Front"))

		val text = render("Text.", images, coverImageId = 5).markdown()

		assertEquals("![Front](images/5~Front.png)\n\n${STORY_ONE}Text.\n", text)
	}

	@Test
	fun `a cover whose file could not be read is left out`() {
		val entries = render("Text.", images = emptyMap(), imageNames = mapOf(5 to "Front"), coverImageId = 5)

		assertEquals(listOf("Story.md"), entries.keys.toList())
		assertTrue(entries.markdown().startsWith("# Story\n"))
	}

	@Test
	fun `an image whose file could not be read is written as its name, and one with no name is dropped`() {
		val markdown = "Before.\n\n${ref(3)}\n\nA ${ref(3)} and ${ref(4)} end.\n\n${ref(4)}\n\nAfter."

		val entries = render(markdown, images = emptyMap(), imageNames = mapOf(3 to "Lost Map"))

		assertEquals(listOf("Story.md"), entries.keys.toList())
		assertEquals("${STORY_ONE}Before.\n\nLost Map\n\nA Lost Map and  end.\n\nAfter.\n", entries.markdown())
	}

	@Test
	fun `markdown in an image name stays literal`() {
		val images = mapOf(image(1, "Star*Map"))

		val text = render(ref(1), images).markdown()

		assertTrue("![Star\\*Map](images/1~Star\u2217Map.png)" in text, text)
	}

	@Test
	fun `a name that needs encoding gets the project's file name and the document points at that entry`() {
		val images = mapOf(image(4, "North/South: Map?"))

		val entries = render(ref(4), images)

		assertEquals(listOf("Story.md", "images/4~North\u2044South\uA789 Map\uFF1F.png"), entries.keys.toList())
		assertTrue("](images/4~North\u2044South\uA789%20Map\uFF1F.png)" in entries.markdown(), entries.markdown())
	}

	@Test
	fun `a destination percent-encodes spaces and parentheses, while the entry keeps them`() {
		val images = mapOf(image(87, "The White Queens Castle", "jpg"), image(88, "Map (old)"))

		val entries = render("${ref(87)}\n\n${ref(88)}", images)

		assertEquals(
			listOf("Story.md", "images/87~The White Queens Castle.jpg", "images/88~Map (old).png"),
			entries.keys.toList(),
		)
		val text = entries.markdown()
		assertTrue("![The White Queens Castle](images/87~The%20White%20Queens%20Castle.jpg)" in text, text)
		assertTrue("![Map \\(old\\)](images/88~Map%20%28old%29.png)" in text, text)
		val destinations = Regex("""!\[[^\]]*]\(([^)]+)\)""").findAll(text).map { it.groupValues[1] }.toList()
		assertEquals(entries.keys.drop(1), destinations.map { URI(it).path })
	}

	@Test
	fun `a destination leaves non-ASCII letters raw`() {
		val images = mapOf(image(5, "Café été"))

		val entries = render(ref(5), images)

		assertEquals(listOf("Story.md", "images/5~Café été.png"), entries.keys.toList())
		assertTrue("![Café été](images/5~Café%20été.png)" in entries.markdown(), entries.markdown())
	}

	@Test
	fun `the document is named with the same stem as the zip`() {
		val projectName = "Vol. 2: The Sea?"
		val entries = render("Text.", projectName = projectName)

		val stem = exportFileName(projectName, exporter.fileExtension).baseName
		assertEquals(listOf("$stem.md"), entries.keys.toList())
		assertTrue(entries.markdown("$stem.md").startsWith("# $projectName\n"))
	}

	@Test
	fun `without chapters the scenes run together under the title`() {
		val images = mapOf(image(1, "Cat"))
		val chapters = listOf(StoryChapter("One", "First."), StoryChapter("Two", ref(1)))

		val text = render(chapters, images, treatTopLevelAsChapters = false).markdown()

		assertEquals("# Story\n\nFirst.\n\n\n![Cat](images/1~Cat.png)\n", text)
	}

	@Test
	fun `entry names are flagged as UTF-8, so a reader that defaults to CP437 still finds them`() {
		val images = mapOf(image(4, "North/South: Map?"), image(6, "Caf\u00e9 \u041a\u0430\u0440\u0442\u0430"))

		// java.util.zip only decodes a name with the given charset when general purpose bit 11 is clear.
		val entries = render("${ref(4)}\n\n${ref(6)}", images, charset = Charset.forName("IBM437"))

		assertEquals(
			listOf(
				"Story.md",
				"images/4~North\u2044South\uA789 Map\uFF1F.png",
				"images/6~Caf\u00e9 \u041a\u0430\u0440\u0442\u0430.png",
			),
			entries.keys.toList(),
		)
	}

	@Test
	fun `a cover the text also shows is packaged once, right after the document`() {
		val images = mapOf(image(1, "Cat"), image(5, "Cover"))
		val markdown = "A.\n\n${ref(5)}\n\n${ref(1)}"

		val entries = render(markdown, images, coverImageId = 5)

		assertEquals(listOf("Story.md", "images/5~Cover.png", "images/1~Cat.png"), entries.keys.toList())
		assertEquals(
			"![Cover](images/5~Cover.png)\n\n${STORY_ONE}A.\n\n" +
				"![Cover](images/5~Cover.png)\n\n![Cat](images/1~Cat.png)\n",
			entries.markdown(),
		)
	}

	@Test
	fun `an image shown twice is packaged once`() {
		val images = mapOf(image(1, "Cat"))
		val markdown = "${ref(1)}\n\nA ${ref(1)} sat."

		val entries = render(markdown, images)

		assertEquals(listOf("Story.md", "images/1~Cat.png"), entries.keys.toList())
		assertEquals("${STORY_ONE}![Cat](images/1~Cat.png)\n\nA ![Cat](images/1~Cat.png) sat.\n", entries.markdown())
	}

	@Test
	fun `reference, blockquote and angle-bracket destinations point at the packaged file`() {
		val images = mapOf(image(1, "Cat"), image(2, "Dog"), image(3, "Owl"))
		val markdown = "A ![x][m] here.\n\n> ${ref(2)}\n\n![image](<hammer://image/3>)\n\n[m]: hammer://image/1"

		val entries = render(markdown, images)

		assertEquals(
			listOf("Story.md", "images/1~Cat.png", "images/2~Dog.png", "images/3~Owl.png"),
			entries.keys.toList(),
		)
		val text = entries.markdown()
		assertTrue("A ![Cat](images/1~Cat.png) here." in text, text)
		assertTrue("> ![Dog](images/2~Dog.png)" in text, text)
		assertTrue("\n![Owl](images/3~Owl.png)\n" in text, text)
		assertTrue("hammer://" !in text && "[m]" !in text, text)
	}

	@Test
	fun `a reference defined in one scene serves a use in another`() {
		val images = mapOf(image(1, "Cat"))
		val chapters = listOf(
			StoryChapter("One", listOf("A ![x][m] here.", "More.")),
			StoryChapter("Two", "Last.\n\n[m]: hammer://image/1"),
		)

		val entries = render(chapters, images)

		assertEquals(listOf("Story.md", "images/1~Cat.png"), entries.keys.toList())
		val text = entries.markdown()
		assertTrue(text.startsWith("${STORY_ONE}A ![Cat](images/1~Cat.png) here.\n\nMore.\n\n## Two\n"), text)
		assertTrue("hammer://" !in text && "[m]" !in text, text)
	}

	@Test
	fun `a name written in place of its image stays prose, even one like a list item`() {
		val entries = render("Before.\n\n${ref(3)}\n\nAfter.", imageNames = mapOf(3 to "1. Map"))

		assertEquals("${STORY_ONE}Before.\n\n1\\. Map\n\nAfter.\n", entries.markdown())
	}
}
