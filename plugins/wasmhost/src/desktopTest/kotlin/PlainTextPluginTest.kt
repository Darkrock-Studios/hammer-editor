import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals

/** The plain text plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class PlainTextPluginTest {

	private val plugin = PlainTextPluginHarness()

	private val chapters = listOf(
		StoryChapter("The Harbor", listOf("She *ran* to the dock.\nThe boat was gone.", "\n\nLater, the rain came.")),
		StoryChapter("Aftermath", listOf("Nobody spoke.")),
	)

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	@Test
	fun `defaults give headed chapters, hash scene breaks, underscored italics, and blank-line paragraphs`() {
		assertEquals(
			"The Harbor\n\nShe _ran_ to the dock.\n\nThe boat was gone.\n\n#\n\nLater, the rain came.\n\n\n\nAftermath\n\nNobody spoke.\n",
			plugin.render(chapters),
		)
	}

	@Test
	fun `the package's defaults are those`() {
		assertEquals(plugin.render(chapters), plugin.renderWithSavedSettings(chapters))
	}

	@Test
	fun `a long code block is indented line by line`() {
		val code = (1..2000).joinToString("\n") { "line $it" }
		val text = plugin.render(listOf(StoryChapter("One", "```\n$code\n```")), treatTopLevelAsChapters = false, paragraphs = "Indented")
		assertEquals((1..2000).joinToString("\n", postfix = "\n") { "\tline $it" }, text)
	}

	@Test
	fun `indented paragraphs with asterisk italics, an asterism break, and no headings`() {
		assertEquals(
			"\tShe *ran* to the dock.\n\tThe boat was gone.\n\n* * *\n\n\tLater, the rain came.\n\n\n\n\tNobody spoke.\n",
			plugin.render(chapters, sceneBreak = "Asterisks", italics = "Asterisks", paragraphs = "Indented", chapterHeadings = false),
		)
	}

	@Test
	fun `a single chapter export has no heading and drops italics when asked`() {
		assertEquals(
			"She ran to the dock.\n\nThe boat was gone.\n\n\nLater, the rain came.\n\n\nNobody spoke.\n",
			plugin.render(chapters, treatTopLevelAsChapters = false, sceneBreak = "Blank", italics = "Dropped"),
		)
	}

	@Test
	fun `an italic run stays whole around bold, and ordered lists keep their numbers`() {
		val one = listOf(StoryChapter("One", "*She **never** came back.*\n\n1. Flour\n2. Eggs\n   1. Beaten\n3. Milk"))
		assertEquals("_She never came back._\n\n1. Flour\n2. Eggs\n  1. Beaten\n3. Milk\n", plugin.render(one, treatTopLevelAsChapters = false))
	}

	@Test
	fun `an in-scene rule is a scene break, even as a blank line`() {
		val one = listOf(StoryChapter("One", "Before.\n\n---\n\nAfter."))
		assertEquals("Before.\n\n#\n\nAfter.\n", plugin.render(one, treatTopLevelAsChapters = false))
		assertEquals("Before.\n\n\nAfter.\n", plugin.render(one, treatTopLevelAsChapters = false, sceneBreak = "Blank"))
	}

	@Test
	fun `indented paragraphs indent every line of a list`() {
		val one = listOf(StoryChapter("One", "- Flour\n- Eggs"))
		assertEquals("\t- Flour\n\t- Eggs\n", plugin.render(one, treatTopLevelAsChapters = false, paragraphs = "Indented"))
	}
}
