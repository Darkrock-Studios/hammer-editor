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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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

/**
 * The Kotlin hello plugin from hammer-plugins, whose parts RECIPES.md shows, run by the host. Runs when
 * HAMMER_PLUGINS points at that checkout.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class HelloPluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val plugin: ClientPlugin by lazy {
		val tree = operation<JsonObject, JsonObject>("scene.tree", "", Access.Read, OperationScope.Content) {
			buildJsonObject {
				putJsonArray("nodes") {
					add(buildJsonObject { put("id", 1); put("name", "Storm"); put("kind", "scene"); put("wordCount", 0); putJsonArray("children") {} })
					add(buildJsonObject { put("id", 2); put("name", "Calm"); put("kind", "scene"); put("wordCount", 0); putJsonArray("children") {} })
				}
			}
		}
		val read = operation<JsonObject, JsonObject>("scene.read", "", Access.Read, OperationScope.Content) { input ->
			buildJsonObject { put("markdown", if (input["id"]!!.jsonPrimitive.int == 1) "Rain fell hard." else "Quiet.") }
		}
		val built = File(System.getenv("HAMMER_PLUGINS"), "kotlin/hello/build/hello.hammerplugin")
		check(built.exists()) { "Run gradle :hello:hammerPlugin in hammer-plugins' kotlin/ first" }
		val download = "/downloads/hello.hammerplugin".toPath()
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
				module { single { OperationRegistry(listOf(tree, read), NoProjects) } },
			)
		}
		registry.plugins.single()
	}

	private fun run(name: String, input: JsonObject = JsonObject(emptyMap()), button: String? = null, onProgress: (String?) -> Unit = {}) =
		runBlocking {
			plugin.actions().single { it.name == name }
				.run(ActionCall("Storm", ActionPlace.Project, null, input, button, onProgress = { onProgress(it.message) }))
		}

	@Test
	fun `its actions declare their outputs, places, and fields`() {
		val actions = plugin.actions().associateBy { it.name }
		assertEquals(ActionOutput.Message, actions.getValue("greet").output)
		assertEquals(ActionOutput.Interactive, actions.getValue("wave").output)
		assertEquals(setOf(ActionPlace.Project, ActionPlace.Scene), actions.getValue("count").places)
		assertIs<ActionField.Scenes>(actions.getValue("count").fields.single())
	}

	@Test
	fun `a message, a document with progress, and interactive buttons`() {
		assertEquals("Hello, Storm!", run("greet").message)

		val progress = mutableListOf<String?>()
		val counts = run("count", JsonObject(mapOf("scenes" to JsonArray(listOf(JsonPrimitive(1))))), onProgress = { progress += it })
		assertEquals("# Word counts\n- Storm: 3", counts.markdown)
		assertEquals(listOf<String?>("Counting Storm"), progress)

		val wave = run("wave")
		assertEquals(listOf("Wave", "Wave twice"), wave.buttons.map { it.label })
		assertEquals("You waved twice", run("wave", button = "twice").message)
	}

	@Test
	fun `its text check underlines each very`() {
		val paragraph = "Café very cold."
		val found = runBlocking { plugin.textDiagnostics().single().diagnose(listOf(paragraph), "en") }.single().single()
		assertEquals("very ", paragraph.substring(found.start, found.end))
		assertEquals(listOf("" to "Remove “very”"), found.fixes.map { it.replacement to it.label })
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
