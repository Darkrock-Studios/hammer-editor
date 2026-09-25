import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackageException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuntimePluginsTest {

	private val fileSystem = FakeFileSystem()
	private val directory = "/config/plugins".toPath()
	private val downloads = "/downloads".toPath().also { fileSystem.createDirectories(it) }

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun runtimePlugins(compiledIn: Set<String> = setOf("plaintext")) =
		RuntimePlugins(fileSystem, directory, compiledIn)

	private fun manifest(id: String = "echo", operations: String = "\"greet\"", format: String = "$id.txt") = """
		id = "$id"
		name = "Echo"
		version = "1.0.0"
		api = 1

		[permissions]
		operations = [$operations]

		[[exporters]]
		format = "$format"
		extension = "txt"
		mime = "text/plain"
		label = "Echo (TXT)"
	""".trimIndent()

	private val settings = """
		[[setting]]
		key = "shout"
		type = "bool"
		label = "Shout"
		default = false
	""".trimIndent()

	private fun pack(
		name: String,
		manifest: String? = manifest(),
		module: String = "export_echo",
		settingsToml: String? = settings,
	): Path {
		val bytes = ByteArrayOutputStream().also { out ->
			ZipOutputStream(out).use { zip ->
				fun entry(entryName: String, content: ByteArray) {
					zip.putNextEntry(ZipEntry(entryName))
					zip.write(content)
					zip.closeEntry()
				}
				manifest?.let { entry("manifest.toml", it.encodeToByteArray()) }
				settingsToml?.let { entry("settings.toml", it.encodeToByteArray()) }
				entry("plugin.wasm", testPlugin(module))
			}
		}.toByteArray()
		return (downloads / "$name.hammerplugin").also { path -> fileSystem.write(path) { write(bytes) } }
	}

	@Test
	fun `an installed package loads as a plugin with its settings`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))

		val loaded = assertIs<WasmPlugin>(plugins.load().single())
		assertEquals("echo", loaded.id)
		assertEquals("Echo", loaded.name)
		assertEquals(listOf("shout"), loaded.settings().map { it.key })
		assertEquals(listOf("greet"), plugins.installed().single().granted)
	}

	@Test
	fun `disabled and uninstalled plugins do not load`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))

		plugins.setEnabled("echo", false)
		assertTrue(plugins.load().isEmpty())
		assertEquals(false, plugins.installed().single().enabled)

		plugins.setEnabled("echo", true)
		plugins.uninstall("echo")
		assertTrue(plugins.load().isEmpty())
		assertTrue(plugins.installed().isEmpty())
	}

	@Test
	fun `bad packages are refused at install`() {
		val plugins = runtimePlugins()
		val bad = listOf(
			pack("no-manifest", manifest = null),
			pack("bad-id", manifest = manifest(id = "Echo Plugin")),
			pack("bad-format", manifest = manifest(format = "txt")),
			pack("bad-settings", settingsToml = "[[setting]]\nkey = \"x\"\ntype = \"colour\"\nlabel = \"X\""),
			pack("bad-module", module = "wasi"),
			pack("twice", manifest = manifest() + "\n\n" + manifest().substringAfter("[[exporters]]").let { "[[exporters]]$it" }),
			(downloads / "not-a-zip.hammerplugin").also { fileSystem.write(it) { writeUtf8("hello") } },
		)
		bad.forEach { assertThrows<PluginPackageException> { plugins.install(it) } }
		assertTrue(plugins.installed().isEmpty())
	}

	@Test
	fun `a plugin cannot take a built-in plugin's id`() {
		assertThrows<PluginPackageException> { runtimePlugins().install(pack("plaintext", manifest(id = "plaintext"))) }
	}

	@Test
	fun `a package damaged after install is skipped, not fatal`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))
		fileSystem.write(directory / "packages" / "echo.hammerplugin") { writeUtf8("damaged") }

		assertTrue(plugins.load().isEmpty())
		assertEquals(null, plugins.installed().single().manifest)
	}

	@Test
	fun `exports send the book and the plugin's settings`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))
		val registry = startKoin(plugins)

		registry.settings("echo")!!.set("shout", JsonPrimitive(true))
		val exporter = GlobalContext.get().get<StoryExporterRegistry>().forFormat("echo.txt")
		assertEquals("Echo (TXT)", exporter.label)

		val sink = Buffer()
		exporter.render(sink, exportInput())
		val request = Json.parseToJsonElement(sink.readUtf8()).jsonObject

		assertEquals(JsonPrimitive(true), request["settings"]!!.jsonObject["shout"])
		assertEquals("Chapter One", request["chapters"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
	}

	@Test
	fun `operations the user did not grant are refused`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", module = "dispatch_echo"))
		val state = directory / RuntimePlugins.STATE_FILE
		fileSystem.write(state) { writeUtf8("[plugins.echo]\nenabled = true\ngranted = []\n") }
		startKoin(plugins)
		val plugin = GlobalContext.get().get<PluginRegistry>().plugins.single() as WasmPlugin

		val reply = plugin.callBlocking("run", """{"operation":"greet","input":{"name":"Ada"}}""".encodeToByteArray())

		val error = Json.parseToJsonElement(reply.decodeToString()).jsonObject["error"]!!.jsonObject
		assertEquals(WasmPlugin.PERMISSION_DENIED, error["kind"]!!.jsonPrimitive.content)
	}

	private fun startKoin(plugins: RuntimePlugins): PluginRegistry {
		val registry = PluginRegistry(plugins.load())
		val greet = operation<Greeting, Greeting>("greet", "", Access.Read) { it }
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { createTomlSerializer() }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
			single { StoryExporterRegistry(getAll()) }
		}
		GlobalContext.startKoin {
			modules(listOf(base) + registry.koinModules() + module {
				single { OperationRegistry(listOf(greet), NoProjects) }
			})
		}
		return registry
	}

	private fun exportInput() = ExportInput(
		projectName = "Storm",
		projectData = null,
		chapters = listOf(StoryChapter("Chapter One", listOf("It rained."))),
		treatTopLevelAsChapters = true,
		language = "en",
		strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
	)

	@Serializable
	data class Greeting(val name: String)

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
