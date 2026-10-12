import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.plugins.plaintext.Italics
import com.darkrockstudios.apps.hammer.plugins.plaintext.ParagraphStyle
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextExporter
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettings
import com.darkrockstudios.apps.hammer.plugins.plaintext.SceneBreak
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlainTextExporterTest {

	private val chapters = listOf(
		StoryChapter(
			"The Harbor",
			listOf("She *ran* to the dock.\nThe boat was gone.", "\n\nLater, the rain came."),
		),
		StoryChapter("Aftermath", listOf("Nobody spoke.")),
	)

	private fun export(settings: PlainTextSettings, treatTopLevelAsChapters: Boolean = true): String {
		val input = ExportInput(
			projectName = "Tide",
			projectData = null,
			chapters = chapters,
			treatTopLevelAsChapters = treatTopLevelAsChapters,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
		)
		return Buffer().also { PlainTextExporter { settings }.render(it, input) }.readUtf8()
	}

	@Test
	fun `defaults give headed chapters, hash scene breaks, underscored italics, and blank-line paragraphs`() {
		assertEquals(
			"""
			The Harbor

			She _ran_ to the dock.

			The boat was gone.

			#

			Later, the rain came.



			Aftermath

			Nobody spoke.

			""".trimIndent(),
			export(PlainTextSettings()),
		)
	}

	@Test
	fun `indented paragraphs with asterisk italics, an asterism break, and no headings`() {
		val settings = PlainTextSettings(
			sceneBreak = SceneBreak.Asterisks,
			italics = Italics.Asterisks,
			paragraphs = ParagraphStyle.Indented,
			chapterHeadings = false,
		)

		assertEquals(
			"\tShe *ran* to the dock.\n\tThe boat was gone.\n\n* * *\n\n\tLater, the rain came.\n\n\n\n\tNobody spoke.\n",
			export(settings),
		)
	}

	@Test
	fun `a single chapter export has no heading and drops italics when asked`() {
		val settings = PlainTextSettings(sceneBreak = SceneBreak.Blank, italics = Italics.Dropped)

		assertEquals(
			"She ran to the dock.\n\nThe boat was gone.\n\n\nLater, the rain came.\n\n\nNobody spoke.\n",
			export(settings, treatTopLevelAsChapters = false),
		)
	}

	@Test
	fun `an italic run stays whole around bold, and ordered lists keep their numbers`() {
		val input = ExportInput(
			projectName = "Tide",
			projectData = null,
			chapters = listOf(StoryChapter("One", "*She **never** came back.*\n\n1. Flour\n2. Eggs\n   1. Beaten\n3. Milk")),
			treatTopLevelAsChapters = false,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
		)
		val text = Buffer().also { PlainTextExporter { PlainTextSettings() }.render(it, input) }.readUtf8()

		assertEquals("_She never came back._\n\n1. Flour\n2. Eggs\n  1. Beaten\n3. Milk\n", text)
	}

	@Test
	fun `an in-scene rule is a scene break, even as a blank line`() {
		val input = ExportInput(
			projectName = "Tide",
			projectData = null,
			chapters = listOf(StoryChapter("One", "Before.\n\n---\n\nAfter.")),
			treatTopLevelAsChapters = false,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
		)
		fun render(settings: PlainTextSettings) =
			Buffer().also { PlainTextExporter { settings }.render(it, input) }.readUtf8()

		assertEquals("Before.\n\n#\n\nAfter.\n", render(PlainTextSettings()))
		assertEquals("Before.\n\n\nAfter.\n", render(PlainTextSettings(sceneBreak = SceneBreak.Blank)))
	}

	@Test
	fun `indented paragraphs indent every line of a list`() {
		val input = ExportInput(
			projectName = "Tide",
			projectData = null,
			chapters = listOf(StoryChapter("One", "- Flour\n- Eggs")),
			treatTopLevelAsChapters = false,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
		)
		val settings = PlainTextSettings(paragraphs = ParagraphStyle.Indented)
		val text = Buffer().also { PlainTextExporter { settings }.render(it, input) }.readUtf8()

		assertEquals("\t- Flour\n\t- Eggs\n", text)
	}
}
