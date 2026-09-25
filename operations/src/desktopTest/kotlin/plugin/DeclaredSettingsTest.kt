package plugin

import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeclaredSettingsTest {

	private val fileSystem = FakeFileSystem()

	private val declarations = parseSettingDeclarations(
		"""
		[[setting]]
		key = "scene_break"
		type = "choice"
		label = "Scene break"
		default = "hash"
		options = [
			{ value = "hash", label = "#" },
			{ value = "blank", label = "Blank line" },
		]

		[[setting]]
		key = "headings"
		type = "bool"
		label = "Chapter headings"
		hint = "Only for chapters."
		default = true

		[[setting]]
		key = "min_length"
		type = "int"
		label = "Shortest word"
		default = 3
		min = 1
		max = 20

		[[setting]]
		key = "title"
		type = "string"
		label = "Title"
		multiline = true
		""".trimIndent()
	)

	private class Declaring(override val id: String, private val declared: List<SettingDeclaration>) : ClientPlugin {
		override fun settings() = declared
	}

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun registry(): PluginRegistry {
		val registry = PluginRegistry().apply { add(Declaring("demo", declarations)) }
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { createTomlSerializer() }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
		}
		GlobalContext.startKoin { modules(base, registry.koinModule()) }
		return registry
	}

	@Test
	fun `parses every type`() {
		val choice = assertIs<SettingDeclaration.Choice>(declarations[0])
		assertEquals(listOf("hash", "blank"), choice.options.map { it.value })
		assertEquals("Only for chapters.", assertIs<SettingDeclaration.Toggle>(declarations[1]).hint)
		val number = assertIs<SettingDeclaration.Number>(declarations[2])
		assertEquals(20, number.max)
		assertEquals(true, assertIs<SettingDeclaration.Text>(declarations[3]).multiline)
	}

	@Test
	fun `unknown types are rejected`() {
		assertThrows<IllegalArgumentException> {
			parseSettingDeclarations("[[setting]]\nkey = \"a\"\ntype = \"colour\"\nlabel = \"A\"")
		}
	}

	@Test
	fun `every key starts at its default`() {
		val values = registry().settings("demo")!!.values.value

		assertEquals(JsonPrimitive("hash"), values["scene_break"])
		assertEquals(JsonPrimitive(true), values["headings"])
		assertEquals(JsonPrimitive(3), values["min_length"])
		assertEquals(JsonPrimitive(""), values["title"])
	}

	@Test
	fun `values the declaration rejects are ignored`() {
		val store = registry().settings("demo")!!

		store.set("scene_break", JsonPrimitive("dots"))
		store.set("min_length", JsonPrimitive(99))
		store.set("headings", JsonPrimitive("yes"))
		store.set("undeclared", JsonPrimitive(1))

		assertEquals(JsonObject(declarations.associate { it.key to it.default }), store.values.value)
	}

	@Test
	fun `values survive a restart, and bad stored values fall back to defaults`() {
		registry().settings("demo")!!.set("min_length", JsonPrimitive(7))
		GlobalContext.stopKoin()
		assertEquals(JsonPrimitive(7), registry().settings("demo")!!.values.value["min_length"])
		GlobalContext.stopKoin()

		val file = getConfigDirectory().toPath() / PluginSettingsDatasource.PLUGINS_DIRECTORY / "demo.toml"
		fileSystem.write(file) { writeUtf8("min_length = \"lots\"\nheadings = false\n") }

		val values = registry().settings("demo")!!.values.value
		assertEquals(JsonPrimitive(3), values["min_length"])
		assertEquals(JsonPrimitive(false), values["headings"])
	}

	@Test
	fun `a plugin without settings has no store`() {
		val registry = registry()
		assertNull(registry.settings("missing"))
	}

	@Test
	fun `a plugin with invalid declarations is refused`() {
		val badDefault = SettingDeclaration.Number("n", "N", defaultValue = 50, max = 10)
		assertThrows<IllegalArgumentException> { PluginRegistry().add(Declaring("bad", listOf(badDefault))) }
		val twice = SettingDeclaration.Toggle("t", "T", defaultValue = true)
		assertThrows<IllegalArgumentException> { PluginRegistry().add(Declaring("dup", listOf(twice, twice))) }
	}
}
