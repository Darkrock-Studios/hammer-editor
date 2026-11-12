import com.darkrockstudios.apps.hammer.common.projecthome.PluginPick
import com.darkrockstudios.apps.hammer.common.projecthome.changesFor
import com.darkrockstudios.apps.hammer.common.projecthome.picked
import com.darkrockstudios.apps.hammer.common.projecthome.pluginChoices
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginChoicesTest {
	private fun plugin(id: String, kind: PluginKind?, vararg languages: String) = object : ClientPlugin {
		override val id = id
		override val name = id.replaceFirstChar { it.uppercase() }
		override val kind = kind
		override val languages = languages.toList()
	}

	private val grammar = PluginKind("grammar", "Grammar checker")
	private val harper = plugin("harper", grammar, "en")
	private val languageTool = plugin("languagetool", grammar)
	private val simple = plugin("simple", grammar, "en", "fr")
	private val names = plugin("names", null)

	@Test
	fun `plugins of a kind that work in the project's language make one choice`() {
		val english = pluginChoices(listOf(harper, languageTool, simple, names), "en-GB").single()
		assertEquals(listOf("harper", "languagetool", "simple"), english.plugins.map { it.id })

		val french = pluginChoices(listOf(harper, languageTool, simple, names), "fr").single()
		assertEquals(listOf("languagetool", "simple"), french.plugins.map { it.id })
	}

	@Test
	fun `a kind with one plugin for the language is no choice`() {
		assertEquals(emptyList(), pluginChoices(listOf(harper, languageTool, simple), "de"))
	}

	@Test
	fun `a choice is labeled by the first translated label, else the first by plugin id`() {
		val french = PluginKind("grammar", "Correcteur", localized = true)
		val choice = pluginChoices(listOf(plugin("zed", french), plugin("alpha", grammar)), null).single()
		assertEquals("Correcteur", choice.label)

		assertEquals("Grammar checker", pluginChoices(listOf(plugin("zed", grammar.copy(label = "Zed's")), plugin("alpha", grammar)), null).single().label)
	}

	@Test
	fun `the pick follows which plugins are on, and picking one turns the rest off`() {
		val choice = pluginChoices(listOf(harper, languageTool, simple), "en").single()

		assertEquals(PluginPick.Several, choice.picked(emptySet()))
		assertEquals(PluginPick.One(simple), choice.picked(setOf("harper", "languagetool")))
		assertEquals(PluginPick.None, choice.picked(setOf("harper", "languagetool", "simple")))

		assertEquals(mapOf("harper" to false, "languagetool" to true, "simple" to false), choice.changesFor(PluginPick.One(languageTool)))
		assertEquals(mapOf("harper" to false, "languagetool" to false, "simple" to false), choice.changesFor(PluginPick.None))
		assertEquals(emptyMap(), choice.changesFor(PluginPick.Several))
	}

	@Test
	fun `a plugin of the kind not offered for the language is turned off by a pick, and shows as one more on`() {
		val french = pluginChoices(listOf(harper, languageTool, simple), "fr").single()

		assertEquals(PluginPick.Several, french.picked(setOf("simple")))
		assertEquals(PluginPick.One(languageTool), french.picked(setOf("harper", "simple")))
		assertEquals(mapOf("languagetool" to true, "simple" to false, "harper" to false), french.changesFor(PluginPick.One(languageTool)))
		assertEquals(mapOf("languagetool" to false, "simple" to false, "harper" to false), french.changesFor(PluginPick.None))
	}
}
