import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The character interview plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class CharacterInterviewPluginTest {

	private val fileSystem = FakeFileSystem()
	private val created = mutableListOf<JsonObject>()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val action: PluginAction by lazy {
		val create = operation<JsonObject, JsonObject>("entry.create", "", Access.Write, OperationScope.Content) { input ->
			created += input
			JsonObject(mapOf("id" to JsonPrimitive(ENTRY_ID)))
		}
		val built = File(PluginRepos.official, "character-interview/build/character-interview.hammerplugin")
		check(built.exists()) { "Run ./build.sh character-interview in hammer-plugins first" }
		val download = "/downloads/character-interview.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), "/cache/plugins".toPath())
		plugins.install(download)
		val registry = PluginRegistry().also(plugins::activate)
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { fileSystem }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
				module { single { OperationRegistry(listOf(create), NoProjects) } },
			)
		}
		registry.plugins.single().actions().single()
	}

	private val start = JsonObject(mapOf("name" to JsonPrimitive("Mara"), "set" to JsonPrimitive("quick")))

	private fun run(input: JsonObject, button: String? = null, from: ActionPage? = null): ActionReply =
		runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, input, button, from?.state)) }

	/** Presses [button] on [page] with its fields at their defaults, apart from [answers]. */
	private fun press(page: ActionPage, button: String, answers: Map<String, String> = emptyMap()): ActionReply {
		val input = page.fields.associate { field -> field.key to (answers[field.key]?.let(::JsonPrimitive) ?: field.default) }
		return run(JsonObject(input), button, page)
	}

	@Test
	fun `it is a kept interview from the project, asking for a name and a set`() {
		assertEquals("interview", action.name)
		assertTrue(action.keep)
		assertEquals(setOf(ActionPlace.Project), action.places)
		val (name, set) = action.fields
		assertEquals("name", name.key)
		val text = assertIs<ActionField.Text>(name)
		assertEquals(TextFormat.Plain, text.format)
		assertTrue(text.required)
		assertEquals("set", set.key)
		val choice = assertIs<SettingDeclaration.Choice>(assertIs<ActionField.Setting>(set).declaration)
		assertEquals(listOf("quick", "full", "proust"), choice.options.map { it.value })
	}

	@Test
	fun `it walks the questions a page at a time, keeps answers going back, and creates and opens the entry`() {
		val first = assertNotNull(run(start).page)
		assertEquals("Mara: Who they are (1 of 4)", first.title)
		assertEquals(listOf("q1", "q2", "q3"), first.fields.map { it.key })
		assertTrue(first.fields.all { it is ActionField.Text && it.format == TextFormat.Plain && it.multiline }, first.fields.toString())
		assertEquals(listOf("back", "next"), first.buttons.map { it.id })
		assertEquals(false, first.buttons.single { it.id == "back" }.validate)
		assertTrue(first.buttons.single { it.id == "next" }.primary)

		val answer = "Mara, though the crew call her *Skip*."
		val second = assertNotNull(press(first, "next", mapOf("q1" to answer)).page)
		assertEquals("Mara: Wants and fears (2 of 4)", second.title)
		val again = assertNotNull(press(second, "back").page)
		assertEquals(first.title, again.title)
		assertEquals(JsonPrimitive(answer), again.fields.single { it.key == "q1" }.default)

		// Pages 2 to 4, then the review.
		var page = again
		repeat(4) { page = assertNotNull(press(page, "next").page, page.title) }
		assertEquals("Mara: Review", page.title)
		val markdown = assertNotNull(page.markdown)
		assertTrue("## Who they are" in markdown, markdown)
		assertTrue("**What do people call you, and what do you wish they called you?**\n\n$answer" in markdown, markdown)
		assertEquals(listOf("back", "create"), page.buttons.map { it.id })

		val done = press(page, "create")
		assertEquals("Added Mara to the encyclopedia", done.message)
		assertEquals(ActionPlace.Entry, done.open?.place)
		assertEquals(ENTRY_ID, done.open?.id)
		assertEquals(null, done.page)
		val entry = created.single()
		assertEquals("Mara", entry["name"]!!.jsonPrimitive.content)
		assertEquals("person", entry["type"]!!.jsonPrimitive.content)
		assertEquals(markdown, entry["text"]!!.jsonPrimitive.content)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}

	private companion object {
		const val ENTRY_ID = 27
	}
}
