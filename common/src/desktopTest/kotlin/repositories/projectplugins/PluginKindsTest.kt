package repositories.projectplugins

import com.darkrockstudios.apps.hammer.common.data.projectplugins.KindedPlugin
import com.darkrockstudios.apps.hammer.common.data.projectplugins.oneOfEachKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginKindsTest {
	private val harper = KindedPlugin("harper", "grammar", listOf("en"))
	private val languageTool = KindedPlugin("languagetool", "grammar")
	private val simple = KindedPlugin("simple", "grammar", listOf("en", "fr"))
	private val names = KindedPlugin("names", "names")
	private val installed = listOf(harper, languageTool, simple, names)

	@Test
	fun `with nothing picked, the first installed for the project's language runs`() {
		assertEquals(setOf("languagetool", "simple"), oneOfEachKind(emptySet(), installed, "en-GB"))
		assertEquals(setOf("harper", "simple"), oneOfEachKind(emptySet(), installed, "fr"))
		assertEquals(setOf("languagetool", "simple"), oneOfEachKind(emptySet(), installed, null))
	}

	@Test
	fun `with none for the language, the first installed runs`() {
		assertEquals(setOf("simple"), oneOfEachKind(emptySet(), listOf(harper, simple), "de"))
	}

	@Test
	fun `a plugin picked by turning the rest off runs, whatever the language`() {
		val picked = setOf("languagetool", "simple")
		assertEquals(picked, oneOfEachKind(picked, installed, "fr"))
	}

	@Test
	fun `a kind turned off entirely stays off`() {
		val none = setOf("harper", "languagetool", "simple")
		assertEquals(none, oneOfEachKind(none, installed, "en"))
	}
}
