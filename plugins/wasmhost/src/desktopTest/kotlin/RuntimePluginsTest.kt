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
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCancelledException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginCache
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackageException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimePluginsTest {

	private val fileSystem = FakeFileSystem()
	private val directory = "/config/plugins".toPath()
	private val cacheDirectory = "/cache/plugins".toPath()
	private val downloads = "/downloads".toPath().also { fileSystem.createDirectories(it) }

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun runtimePlugins(locale: String? = null) = RuntimePlugins(fileSystem, directory, cacheDirectory, locale)

	private fun manifest(
		id: String = "echo",
		operations: String = "\"greet\"",
		format: String = "$id.txt",
		command: String? = null,
		action: String? = null,
		output: String? = null,
	) = """
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
	""".trimIndent() + command?.let { "\n\n[[commands]]\nname = \"$it\"\nhelp = \"Echoes.\"" }.orEmpty() +
		action?.let { "\n\n[[actions]]\nname = \"$it\"\nlabel = \"Echo it\"" }.orEmpty() +
		output?.let { "\noutput = \"$it\"" }.orEmpty()

	private fun diagnostics(name: String) = "\n\n[[diagnostics]]\nname = \"$name\"\nlabel = \"Grammar\""

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
		files: Map<String, String> = emptyMap(),
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
				files.forEach { (entryName, content) -> entry(entryName, content.encodeToByteArray()) }
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
			pack("destructive-scope", manifest = manifest(operations = "\"content:destructive\"")),
			pack("unknown-scope", manifest = manifest(operations = "\"everything:read\"")),
			pack("operation-command", manifest = manifest(command = "scene")),
			pack("help-command", manifest = manifest(command = "help")),
			pack("bad-command", manifest = manifest(command = "Echo Back")),
			pack("bad-action", manifest = manifest(action = "Echo It")),
			pack("bad-output", manifest = manifest(action = "report", output = "dialog")),
			pack("bad-diagnostics", manifest = manifest() + diagnostics("Grammar Check")),
			pack("diagnostics-twice", manifest = manifest() + diagnostics("grammar") + diagnostics("grammar")),
			pack("no-memory", manifest = manifest() + "\n\n[limits]\nmemory = 0"),
			pack("too-much-memory", manifest = manifest() + "\n\n[limits]\nmemory = 2048"),
			pack("command-twice", manifest = manifest(command = "echo") + "\n\n[[commands]]\nname = \"echo\"\nhelp = \"Again.\""),
			(downloads / "not-a-zip.hammerplugin").also { fileSystem.write(it) { writeUtf8("hello") } },
		)
		bad.forEach { assertThrows<PluginPackageException> { plugins.install(it) } }
		assertTrue(plugins.installed().isEmpty())
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

	@Test
	fun `a command answers each line of input with the plugin's saved settings`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest(command = "echo-back")))
		fileSystem.write(directory / "echo.toml") { writeUtf8("shout = true\n") }
		val command = plugins.load().single().cliCommands().single()
		assertEquals("echo-back", command.name)
		assertEquals("Echoes.", command.help)

		val stdout = Buffer()
		val io = CliIo(Buffer().writeUtf8("first\nsecond"), stdout, Buffer())
		val code = runBlocking { command.run(listOf("--verbose"), io, UnusedDispatcher) }

		assertEquals(0, code)
		val requests = stdout.readUtf8().lines().filter { it.isNotEmpty() }.map { Json.parseToJsonElement(it).jsonObject }
		assertEquals(listOf("first", "second"), requests.map { it["line"]!!.jsonPrimitive.content })
		assertEquals(JsonPrimitive(true), requests.first()["settings"]!!.jsonObject["shout"])
		assertEquals("echo-back", requests.first()["command"]!!.jsonPrimitive.content)
		assertEquals(listOf("--verbose"), requests.first()["args"]!!.jsonArray.map { it.jsonPrimitive.content })
	}

	@Test
	fun `a project action runs the module on the project with the plugin's settings`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest(action = "report")))
		val registry = startKoin(plugins)
		registry.settings("echo")!!.set("shout", JsonPrimitive(true))

		val action = registry.plugins.single().actions().single()
		assertEquals("Echo it", action.label)
		assertEquals(ActionOutput.Message, action.output)
		assertEquals(setOf(ActionPlace.Project), action.places)
		val reply = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))) }
		val request = Json.parseToJsonElement(reply.message!!).jsonObject

		assertEquals("report", request["action"]!!.jsonPrimitive.content)
		assertEquals("Storm", request["project"]!!.jsonPrimitive.content)
		assertEquals(JsonPrimitive(true), request["settings"]!!.jsonObject["shout"])
		assertEquals(JsonObject(emptyMap()), request["input"])
		assertEquals("project", request["context"]!!.jsonObject["place"]!!.jsonPrimitive.content)
	}

	@Test
	fun `an action declares its places and fields, and gets the input and the item it was run on`() {
		val plugins = runtimePlugins()
		val action = """
			places = ["scene", "entry"]

			[[actions.field]]
			key = "count"
			type = "int"
			label = "How many"
			default = 5
			max = 10

			[[actions.field]]
			key = "scenes"
			type = "scenes"
			label = "Scenes"
			multiple = false
		""".trimIndent()
		plugins.install(pack("echo", manifest(action = "report") + "\n" + action))

		val loaded = startKoin(plugins).plugins.single().actions().single()
		assertEquals(setOf(ActionPlace.Scene, ActionPlace.Entry), loaded.places)
		val (count, scenes) = loaded.fields
		assertEquals(5L, ((count as ActionField.Setting).declaration as SettingDeclaration.Number).defaultValue)
		assertEquals(false, (scenes as ActionField.Scenes).multiple)

		val input = JsonObject(mapOf("count" to JsonPrimitive(3), "scenes" to JsonPrimitive(12)))
		val reply = runBlocking { loaded.run(ActionCall("Storm", ActionPlace.Entry, 7, input, button = "again")) }
		val request = Json.parseToJsonElement(reply.message!!).jsonObject
		assertEquals(input, request["input"])
		assertEquals(Json.parseToJsonElement("""{"place":"entry","id":7}"""), request["context"])
		assertEquals("again", request["button"]!!.jsonPrimitive.content)
	}

	private val translatable = manifest(action = "report") + """

		[[actions.field]]
		key = "tone"
		type = "choice"
		label = "Tone"
		options = [{ value = "warm", label = "Warm" }, { value = "cool", label = "Cool" }]
	""".trimIndent()

	private val translations = mapOf(
		"locales/fr.toml" to """
			name = "Écho"

			[exporters."echo.txt"]
			label = "Écho (TXT)"

			[actions.report]
			label = "Répète-le"

			[actions.report.fields.tone]
			label = "Ton"
			options = { warm = "Chaleureux" }

			[settings.shout]
			label = "Crier"
		""".trimIndent(),
		"locales/fr-CA.toml" to """
			[actions.report]
			label = "Répète ça"
		""".trimIndent(),
	)

	@Test
	fun `a package's words come in the UI's language, the most specific translation first`() {
		val plugins = runtimePlugins(locale = "fr-CA")
		plugins.install(pack("echo", translatable, files = translations))
		val plugin = startKoin(plugins).plugins.single()

		assertEquals("Écho", plugin.name)
		assertEquals("Écho (TXT)", plugin.exporters().single().label)
		assertEquals("Crier", plugin.settings().single().label)
		val action = plugin.actions().single()
		assertEquals("Répète ça", action.label)
		val tone = (action.fields.single() as ActionField.Setting).declaration as SettingDeclaration.Choice
		assertEquals("Ton", tone.label)
		assertEquals(listOf("Chaleureux", "Cool"), tone.options.map { it.label })
		assertEquals("Écho", plugins.installed().single().manifest?.name)
	}

	@Test
	fun `without a translation for the UI's language, a package keeps its own words, and calls are told the language`() {
		val plugins = runtimePlugins(locale = "de-DE")
		plugins.install(pack("echo", translatable, files = translations))
		val action = startKoin(plugins).plugins.single().actions().single()

		assertEquals("Echo it", action.label)
		val reply = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))) }
		assertEquals("de-DE", Json.parseToJsonElement(reply.message!!).jsonObject["locale"]!!.jsonPrimitive.content)
	}

	@Test
	fun `a broken translation, or one not named for a language, is refused`() {
		val plugins = runtimePlugins()
		assertThrows<PluginPackageException> { plugins.install(pack("echo", files = mapOf("locales/fr.toml" to "name = [1, 2"))) }
		assertThrows<PluginPackageException> { plugins.install(pack("echo", files = mapOf("locales/français.toml" to "name = \"Écho\""))) }
	}

	@Test
	fun `actions with unknown places or broken fields are refused`() {
		val plugins = runtimePlugins()
		fun refused(extra: String) = assertThrows<PluginPackageException> {
			plugins.install(pack("echo", manifest(action = "report") + "\n" + extra.trimIndent()))
		}
		refused("places = [\"sidebar\"]")
		refused("places = []")
		refused("[[actions.field]]\nkey = \"a\"\ntype = \"colour\"\nlabel = \"A\"")
		refused("[[actions.field]]\nkey = \"a\"\ntype = \"int\"\nlabel = \"A\"\ndefault = 20\nmax = 10")
		refused("[[actions.field]]\nkey = \"a\"\ntype = \"bool\"\nlabel = \"A\"\n\n[[actions.field]]\nkey = \"a\"\ntype = \"bool\"\nlabel = \"B\"")
	}

	@Test
	fun `an interactive action's reply is its markdown, buttons, and message`() {
		val plugins = runtimePlugins()
		plugins.install(pack("names", manifest(id = "names", action = "report", output = "interactive"), module = "interactive"))
		plugins.install(pack("broken", manifest(id = "broken", action = "report", output = "interactive"), module = "progress"))
		val (names, broken) = startKoin(plugins).plugins.sortedByDescending { it.id }.map { it.actions().single() }
		val call = ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))

		val reply = runBlocking { names.run(call) }
		assertEquals(listOf("# Names", "Made"), listOf(reply.markdown, reply.message))
		assertEquals(listOf("a" to "Aldric"), reply.buttons.map { it.id to it.label })
		// It replies "done", which is not JSON.
		assertThrows<PluginException> { runBlocking { broken.run(call) } }
	}

	@Test
	fun `an action reports its progress, and a cancelled one stops at its next report`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest(action = "report"), module = "progress"))
		val action = startKoin(plugins).plugins.single().actions().single()
		val reported = mutableListOf<Pair<Float?, String?>>()

		val reply = runBlocking {
			action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()), onProgress = { reported += it.fraction to it.message }))
		}
		assertEquals("done", reply.message)
		assertEquals(listOf<Pair<Float?, String?>>(0.5f to "Half"), reported)

		assertThrows<ActionCancelledException> {
			runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()), cancelled = { true })) }
		}
		assertEquals("done", runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))) }.message)
	}

	@Test
	fun `a diagnostics check keeps only issues inside the paragraphs, at UTF-16 offsets`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest() + diagnostics("grammar"), module = "diagnose"))

		val check = startKoin(plugins).plugins.single().textDiagnostics().single()
		val found = runBlocking { check.diagnose(listOf("Ét the the end", "Fine."), "en") }

		assertEquals("Grammar", check.label)
		assertEquals(2, found.size)
		val issue = found[0].single()
		assertEquals(listOf(3, 10, "Repeated word"), listOf(issue.start, issue.end, issue.message))
		assertEquals(listOf("the" to "the", "" to "Remove the repeat"), issue.fixes.map { it.replacement to it.label })
		assertTrue(found[1].isEmpty())
	}

	@Test
	fun `a module needing more memory than the default installs only when its manifest asks for it`() {
		val plugins = runtimePlugins()
		assertThrows<PluginPackageException> { plugins.install(pack("small", manifest(id = "small", action = "run"), module = "big_memory")) }
		plugins.install(pack("big", manifest(id = "big", action = "run") + "\n\n[limits]\nmemory = 128", module = "big_memory"))

		val action = startKoin(plugins).plugins.single().actions().single()
		assertEquals(null, runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))) }.message)
	}

	@Test
	fun `a module that cannot check leaves the text unmarked`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest() + diagnostics("grammar")))

		val check = startKoin(plugins).plugins.single().textDiagnostics().single()

		assertEquals(listOf(emptyList(), emptyList()), runBlocking { check.diagnose(listOf("One.", "Two."), null) }.map { it.toList() })
	}

	@Test
	fun `an action declaring document output is shown as a document`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest(action = "report", output = "document")))

		assertEquals(ActionOutput.Document, startKoin(plugins).plugins.single().actions().single().output)
	}

	@Test
	fun `a plugin adding a command another has is not activated`() {
		val plugins = runtimePlugins()
		plugins.install(pack("one", manifest(id = "one", command = "echo-back")))
		plugins.install(pack("two", manifest(id = "two", command = "echo-back")))

		assertEquals(listOf("one"), PluginRegistry().also(plugins::activate).plugins.map { it.id })
	}

	@Test
	fun `once activated, changes apply to the registry at once`() {
		val plugins = runtimePlugins()
		val registry = PluginRegistry().also(plugins::activate)

		plugins.install(pack("echo"))
		assertEquals(listOf("echo.txt"), registry.exporters().map { it.formatId })
		plugins.setEnabled("echo", false)
		assertTrue(registry.plugins.isEmpty())
		plugins.setEnabled("echo", true)
		assertEquals(listOf("echo"), registry.plugins.map { it.id })
		plugins.uninstall("echo")
		assertTrue(registry.plugins.isEmpty())
	}

	@Test
	fun `reinstalling replaces the active plugin`() {
		val plugins = runtimePlugins()
		val registry = PluginRegistry().also(plugins::activate)
		plugins.install(pack("echo"))
		val first = registry.plugins.single()

		plugins.install(pack("echo-again"))

		assertTrue(registry.plugins.single() !== first)
	}

	@Test
	fun `reinstalling or uninstalling clears the plugin's cache`() {
		val plugins = runtimePlugins()
		val cache = PluginCache(fileSystem, cacheDirectory / "echo")
		plugins.install(pack("echo"))
		cache.set("scene".encodeToByteArray(), byteArrayOf(1))

		plugins.install(pack("echo-again"))
		assertNull(cache.get("scene".encodeToByteArray()))

		cache.set("scene".encodeToByteArray(), byteArrayOf(1))
		plugins.uninstall("echo")
		assertNull(cache.get("scene".encodeToByteArray()))
	}

	@Test
	fun `a change the registry would refuse is not made`() {
		val plugins = runtimePlugins()
		val registry = PluginRegistry().also(plugins::activate)
		plugins.install(pack("one", manifest(id = "one", command = "echo-back")))

		assertThrows<PluginPackageException> { plugins.install(pack("two", manifest(id = "two", command = "echo-back"))) }
		assertEquals(listOf("one"), plugins.installed().map { it.id })

		plugins.setEnabled("one", false)
		plugins.install(pack("two", manifest(id = "two", command = "echo-back")))
		assertThrows<PluginPackageException> { plugins.setEnabled("one", true) }
		assertEquals(false, plugins.installed().single { it.id == "one" }.enabled)
		assertEquals(listOf("two"), registry.plugins.map { it.id })
	}

	private object UnusedDispatcher : Dispatcher {
		override suspend fun dispatch(operation: String, input: JsonElement): JsonElement = error("unused")
		override suspend fun operations(): List<OperationDescriptor> = error("unused")
	}

	private fun startKoin(plugins: RuntimePlugins): PluginRegistry {
		val registry = PluginRegistry().also(plugins::activate)
		val greet = operation<Greeting, Greeting>("greet", "", Access.Read, OperationScope.Content) { it }
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { createTomlSerializer() }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
			single { StoryExporterRegistry(getAll(), getAll()) }
		}
		GlobalContext.startKoin {
			modules(listOf(base) + listOf(registry.koinModule()) + module {
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
