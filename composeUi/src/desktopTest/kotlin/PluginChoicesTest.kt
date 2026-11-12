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
	fun `every plugin of a kind is in its choice, those for the project's language first`() {
		val english = pluginChoices(listOf(harper, languageTool, simple, names), "en-GB").single()
		assertEquals(listOf("harper", "languagetool", "simple"), english.plugins.map { it.id })

		val french = pluginChoices(listOf(harper, languageTool, simple, names), "fr").single()
		assertEquals(listOf("languagetool", "simple", "harper"), french.plugins.map { it.id })
	}

	@Test
	fun `a kind with one plugin is no choice`() {
		assertEquals(emptyList(), pluginChoices(listOf(harper, names), "en"))
	}

	@Test
	fun `a choice is labeled by the first translated label, else the first by plugin id`() {
		val french = PluginKind("grammar", "Correcteur", localized = true)
		val choice = pluginChoices(listOf(plugin("zed", french), plugin("alpha", grammar)), null).single()
		assertEquals("Correcteur", choice.label)

		assertEquals("Grammar checker", pluginChoices(listOf(plugin("zed", grammar.copy(label = "Zed's")), plugin("alpha", grammar)), null).single().label)
	}

	@Test
	fun `the pick is the plugin on, and picking one turns the rest off`() {
		val choice = pluginChoices(listOf(harper, languageTool, simple), "en").single()

		assertEquals(PluginPick.One(simple), choice.picked(setOf("harper", "languagetool")))
		assertEquals(PluginPick.None, choice.picked(setOf("harper", "languagetool", "simple")))

		assertEquals(mapOf("harper" to false, "languagetool" to true, "simple" to false), choice.changesFor(PluginPick.One(languageTool)))
		assertEquals(mapOf("harper" to false, "languagetool" to false, "simple" to false), choice.changesFor(PluginPick.None))
	}
}
