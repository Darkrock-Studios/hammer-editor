import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
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
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
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

/** The fantasy names plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class FantasyNamesPluginTest {

	private val fileSystem = FakeFileSystem()
	private val created = mutableListOf<JsonObject>()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val action: PluginAction by lazy {
		val create = operation<JsonObject, JsonObject>("entry.create", "", Access.Write, OperationScope.Content) { input ->
			created += input
			JsonObject(emptyMap())
		}
		val built = File(PluginRepos.official, "fantasy-names/build/fantasy-names.hammerplugin")
		check(built.exists()) { "Run ./build.sh fantasy-names in hammer-plugins first" }
		val download = "/downloads/fantasy-names.hammerplugin".toPath()
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
				module { single { OperationRegistry(listOf(create), NoProjects, ::ProjectSyncGateRepository) } },
			)
		}
		registry.plugins.single().actions().single()
	}

	private val input = JsonObject(
		mapOf(
			"style" to JsonPrimitive("dwarvish"),
			"gender" to JsonPrimitive("feminine"),
			"count" to JsonPrimitive(3),
			"bynames" to JsonPrimitive(false),
		)
	)

	private fun run(input: JsonObject, button: String? = null, state: JsonElement? = null) =
		runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, input, button, state)) }

	private fun ActionPage.names() = markdown!!.lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") }

	@Test
	fun `its first page asks for a style, given names, a count, and second names, from the project and entries`() {
		assertEquals(listOf("style", "gender", "count", "bynames"), action.fields.map { it.key })
		assertEquals(setOf(ActionPlace.Project, ActionPlace.Entry), action.places)
		assertTrue(action.fields.all { it is ActionField.Setting })
	}

	@Test
	fun `a name's button adds it as a person, and the same list comes back with it marked`() {
		val first = run(input).page!!
		val names = first.names()
		assertEquals(3, names.size)
		val (inline, footer) = first.buttons.partition { it.inline }
		assertEquals(names, inline.map { it.label })
		assertEquals(names.indices.map { "add:$it" }, inline.map { it.id })
		assertEquals(listOf("More names"), footer.map { it.label })
		assertNotNull(first.state)

		// A page of names has no fields, so the next call's input is empty and the plugin works from its state.
		val second = run(JsonObject(emptyMap()), button = "add:0", state = first.state)

		assertEquals(names.first(), created.single()["name"]!!.jsonPrimitive.content)
		assertEquals("person", created.single()["type"]!!.jsonPrimitive.content)
		assertEquals("Added ${names.first()} to the encyclopedia", second.message)
		val marked = second.page!!
		assertTrue("- ${names.first()} (added)" in marked.markdown!!, marked.markdown)
		assertEquals(names.drop(1), marked.buttons.filter { it.inline }.map { it.label })

		val more = run(JsonObject(emptyMap()), button = footer.single().id, state = marked.state).page!!
		assertEquals(3, more.names().size)
		assertTrue(more.names() != names, more.names().toString())
	}

	@Test
	fun `every style it offers has names in its package`() {
		val styles = assertIs<SettingDeclaration.Choice>((action.fields.first { it.key == "style" } as ActionField.Setting).declaration)
		styles.options.forEach { style ->
			val styled = JsonObject(input + ("style" to JsonPrimitive(style.value)))
			assertEquals(3, run(styled).page!!.names().size, style.value)
		}
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
