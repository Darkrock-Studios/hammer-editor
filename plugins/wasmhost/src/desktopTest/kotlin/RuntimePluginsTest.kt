import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginCache
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginHttp
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginHttpResponse
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackageException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginPackage
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Source
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.test.assertContentEquals
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
		extension: String = "txt",
		mime: String = "text/plain",
		command: String? = null,
		action: String? = null,
		output: String? = null,
		exporterKeys: String? = null,
	) = """
		id = "$id"
		name = "Echo"
		version = "1.0.0"
		api = 1

		[permissions]
		operations = [$operations]

		[[exporters]]
		format = "$format"
		extension = "$extension"
		mime = "$mime"
		label = "Echo (TXT)"
	""".trimIndent() + exporterKeys?.let { "\n$it" }.orEmpty() + command?.let { "\n\n[[commands]]\nname = \"$it\"\nhelp = \"Echoes.\"" }.orEmpty() +
		action?.let { "\n\n[[actions]]\nname = \"$it\"\nlabel = \"Echo it\"" }.orEmpty() +
		output?.let { "\noutput = \"$it\"" }.orEmpty()

	private fun diagnostics(name: String, scope: String? = null) =
		"\n\n[[diagnostics]]\nname = \"$name\"\nlabel = \"Grammar\"" + scope?.let { "\nscope = \"$it\"" }.orEmpty()

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
		wasm: ByteArray = testPlugin(module),
		settingsToml: String? = settings,
		files: Map<String, String> = emptyMap(),
		binaries: Map<String, ByteArray> = emptyMap(),
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
				entry("plugin.wasm", wasm)
				files.forEach { (entryName, content) -> entry(entryName, content.encodeToByteArray()) }
				binaries.forEach { (entryName, content) -> entry(entryName, content) }
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
			pack("has-output", manifest = manifest(action = "report", output = "message")),
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
	fun `an export format asking for the cover without images is refused`() {
		val error = assertThrows<PluginPackageException> {
			runtimePlugins().inspect(pack("cover", manifest(exporterKeys = "cover = true")))
		}
		assertTrue("cover without images" in error.message!!, error.message)
		runtimePlugins().inspect(pack("both", manifest(exporterKeys = "images = true\ncover = true")))
	}

	@Test
	fun `an export format with an unknown output is refused`() {
		val error = assertThrows<PluginPackageException> {
			runtimePlugins().inspect(pack("bogus", manifest(exporterKeys = "output = \"bogus\"")))
		}
		assertEquals("Export format 'echo.txt' has unknown output 'bogus'", error.message)
		listOf("file", "archive").forEach { runtimePlugins().inspect(pack(it, manifest(exporterKeys = "output = \"$it\""))) }
	}

	@Test
	fun `a package asking to log in, log out, or sync is refused, and one reading the account is not`() {
		val plugins = runtimePlugins()

		listOf("account:write", "account.login", "account.logout", "sync.run").forEach { entry ->
			val error = assertThrows<PluginPackageException> { plugins.inspect(pack(entry.replace(':', '-'), manifest(operations = "\"$entry\""))) }
			assertTrue(entry in error.message!!, error.message)
		}
		plugins.inspect(pack("status", manifest(operations = "\"account:read\"")))
	}

	@Test
	fun `a package installed asking to change the account no longer loads, and Settings shows it cannot be read`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))
		val older = pack("older", manifest(operations = "\"greet\", \"account.login\", \"account:write\""))
		fileSystem.copy(older, directory / "packages" / "echo.hammerplugin")
		fileSystem.write(directory / RuntimePlugins.STATE_FILE) {
			writeUtf8("[plugins.echo]\nenabled = true\ngranted = [\"greet\", \"account.login\", \"account:write\"]\n")
		}

		assertTrue(plugins.load().isEmpty())
		assertNull(plugins.installed().single { it.id == "echo" }.manifest)
	}

	@Test
	fun `a package damaged after install is skipped, not fatal`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo"))
		fileSystem.write(directory / "packages" / "echo.hammerplugin") { writeUtf8("damaged") }

		assertTrue(plugins.load().isEmpty())
		assertEquals(null, plugins.installed().single().manifest)
	}

	/** Reading any of [failing] throws what it maps to. */
	private class FailingFileSystem(delegate: FileSystem, private val failing: Map<Path, () -> Throwable>) : ForwardingFileSystem(delegate) {
		override fun source(file: Path): Source = failing[file]?.let { throw it() } ?: super.source(file)
		override fun openReadOnly(file: Path): FileHandle = failing[file]?.let { throw it() } ?: super.openReadOnly(file)
	}

	private fun failingPlugins(vararg failing: Pair<String, () -> Throwable>) = RuntimePlugins(
		FailingFileSystem(fileSystem, failing.associate { (name, error) -> directory / name to error }),
		directory,
		cacheDirectory,
	)

	@Test
	fun `a package that fails to load in any way is skipped, and the others still load`() {
		runtimePlugins().apply {
			install(pack("one", manifest(id = "one")))
			install(pack("two", manifest(id = "two")))
		}
		val plugins = failingPlugins("packages/one.hammerplugin" to { IllegalStateException("zip reader bug") })

		val registry = PluginRegistry().also(plugins::activate)

		assertEquals(listOf("two"), registry.plugins.map { it.id })
	}

	@Test
	fun `a state file that fails to read starts with no plugins`() {
		runtimePlugins().install(pack("echo"))
		val plugins = failingPlugins(RuntimePlugins.STATE_FILE to { IndexOutOfBoundsException("parser bug") })

		val registry = PluginRegistry().also(plugins::activate)

		assertTrue(registry.plugins.isEmpty())
	}

	@Test
	fun `an unreadable state file is kept aside, not written over`() {
		fileSystem.createDirectories(directory)
		fileSystem.write(directory / RuntimePlugins.STATE_FILE) { writeUtf8("plugins = [") }
		fileSystem.write(directory / "${RuntimePlugins.STATE_FILE}.unreadable") { writeUtf8("older") }
		val plugins = runtimePlugins()

		plugins.install(pack("echo"))

		assertEquals("plugins = [", fileSystem.read(directory / "${RuntimePlugins.STATE_FILE}.unreadable-2") { readUtf8() })
		assertEquals(listOf("echo"), plugins.installed().map { it.id })
	}

	private fun dieWhileActivating(id: String) {
		assertThrows<OutOfMemoryError> {
			failingPlugins("packages/$id.hammerplugin" to { OutOfMemoryError("test") }).activate(PluginRegistry())
		}
	}

	@Test
	fun `after a launch that died loading a plugin, that plugin stays off and the others load`() {
		runtimePlugins().apply {
			install(pack("one", manifest(id = "one")))
			install(pack("two", manifest(id = "two")))
		}
		dieWhileActivating("one")

		repeat(2) {
			val next = runtimePlugins()
			assertEquals(listOf("two"), PluginRegistry().also(next::activate).plugins.map { it.id })
			val one = next.installed().first { it.id == "one" }
			assertEquals(false, one.enabled)
			assertTrue(one.turnedOffAfterCrash)
		}
	}

	@Test
	fun `a plugin turned off after a crash can be turned on again`() {
		runtimePlugins().apply {
			install(pack("one", manifest(id = "one")))
			install(pack("two", manifest(id = "two")))
		}
		dieWhileActivating("one")
		val plugins = runtimePlugins()
		val registry = PluginRegistry().also(plugins::activate)

		plugins.setEnabled("one", true)

		assertEquals(setOf("one", "two"), registry.plugins.map { it.id }.toSet())
		assertEquals(false, plugins.installed().first { it.id == "one" }.turnedOffAfterCrash)
		assertEquals(setOf("one", "two"), PluginRegistry().also(runtimePlugins()::activate).plugins.map { it.id }.toSet())
	}

	@Test
	fun `a launch that died turning a plugin on turns it off at the next`() {
		runtimePlugins().apply {
			install(pack("echo"))
			setEnabled("echo", false)
		}
		val dying = failingPlugins("packages/echo.hammerplugin" to { OutOfMemoryError("test") })
		dying.activate(PluginRegistry())
		assertThrows<OutOfMemoryError> { dying.setEnabled("echo", true) }

		val next = runtimePlugins()
		next.activate(PluginRegistry())

		assertTrue(next.installed().single().turnedOffAfterCrash)
	}

	@Test
	fun `an unguarded activation neither turns off a plugin after a crash nor clears the mark`() {
		runtimePlugins().install(pack("echo"))
		dieWhileActivating("echo")

		val unguarded = PluginRegistry().also { runtimePlugins().activate(it, crashGuard = false) }

		assertEquals(listOf("echo"), unguarded.plugins.map { it.id })
		assertTrue(PluginRegistry().also(runtimePlugins()::activate).plugins.isEmpty())
	}

	@Test
	fun `TOML nested too deep for the parser is refused, not left to overflow the stack`() {
		// Each well under the size limit on a text, and far deeper than the parser's stack can follow.
		val depth = 50_000
		val arrays = "deep = " + "[".repeat(depth) + "]".repeat(depth)
		val tables = "deep = " + "{a=".repeat(depth) + "1" + "}".repeat(depth)
		val dotted = "a.".repeat(depth) + "a = 1"
		val header = "[" + "a.".repeat(depth) + "a]"
		val packages = listOf(
			pack("deep-manifest", manifest = manifest() + "\n" + arrays),
			pack("deep-header", manifest = manifest() + "\n" + header),
			pack("deep-settings", settingsToml = settings + "\n" + tables),
			pack("deep-translation", files = mapOf("locales/fr.toml" to dotted)),
		)

		packages.forEach { assertThrows<PluginPackageException> { runtimePlugins().inspect(it) } }
	}

	@Test
	fun `brackets and dots in strings and comments are not nesting`() {
		val noise = "[{.".repeat(100)
		val noisy = manifest().replace(
			"api = 1",
			"api = 1\ndescription = '$noise'\nnote = \"a\\\".$noise\"\nlong = \"\"\"\n$noise\"\"\"\n# $noise",
		)

		runtimePlugins().inspect(pack("noisy", manifest = noisy))
	}

	@Test
	fun `a state file nested too deep for the parser is kept aside`() {
		fileSystem.createDirectories(directory)
		val deep = "plugins = " + "[".repeat(50_000) + "]".repeat(50_000)
		fileSystem.write(directory / RuntimePlugins.STATE_FILE) { writeUtf8(deep) }

		val registry = PluginRegistry().also(runtimePlugins()::activate)

		assertTrue(registry.plugins.isEmpty())
		assertEquals(deep, fileSystem.read(directory / "${RuntimePlugins.STATE_FILE}.unreadable") { readUtf8() })
	}

	@Test
	fun `a module the limiter or the runtime cannot handle is refused at install`() {
		// A last section declaring more bytes than follow it.
		val overrun = testPlugin("export_echo") + byteArrayOf(0x00, 0x10, 0x01)
		// A table whose minimum, 2^31, does not fit an Int.
		val hugeTable = MODULE_HEADER + byteArrayOf(0x04, 0x08, 0x01, 0x70, 0x00) + U32_2_POW_31

		listOf(overrun, hugeTable).forEachIndexed { i, wasm ->
			assertThrows<PluginPackageException> { runtimePlugins().inspect(pack("bad-$i", wasm = wasm)) }
		}
	}

	@Test
	fun `a package larger than any accepted is refused before it is read`() {
		val echo = pack("echo")
		val reportingHuge = object : ForwardingFileSystem(fileSystem) {
			override fun metadataOrNull(path: Path) = super.metadataOrNull(path)?.copy(size = PluginPackage.MAX_PACKAGE_BYTES + 1)
		}

		assertThrows<PluginPackageException> { RuntimePlugins(reportingHuge, directory, cacheDirectory).inspect(echo) }
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
	fun `an exporter asking for images and the cover is sent their files and the cover's id`() {
		val (exporter, request) = export(
			"pics",
			"images = true\ncover = true",
			exportInput(
				scene = "![image](hammer://image/1)",
				projectData = ProjectData(coverImageId = 5),
				images = listOf(image(5, "Cover", "webp", byteArrayOf(5, 5)), image(1, "Cat", "png", byteArrayOf(1, 2, 3, 4))),
			),
		)

		assertTrue(exporter.embedsImages)
		assertTrue(exporter.embedsCover)
		assertTrue(exporter.needsProjectData)
		val images = request["images"]!!.jsonArray.map { it.jsonObject }
		assertEquals(listOf(1, 5), images.map { it["id"]!!.jsonPrimitive.int })
		assertEquals(
			mapOf("id" to "1", "name" to "Cat", "extension" to "png", "mimeType" to "image/png", "content" to "AQIDBA=="),
			images[0].mapValues { it.value.jsonPrimitive.content },
		)
		assertContentEquals(byteArrayOf(1, 2, 3, 4), java.util.Base64.getDecoder().decode(images[0]["content"]!!.jsonPrimitive.content))
		assertEquals("image/webp", images[1]["mimeType"]!!.jsonPrimitive.content)
		assertEquals(5, request["coverImageId"]!!.jsonPrimitive.int)
		assertEquals(
			"![image](hammer://image/1)",
			request["chapters"]!!.jsonArray[0].jsonObject["scenes"]!!.jsonArray.single().jsonPrimitive.content,
		)
	}

	@Test
	fun `a cover whose file was not read has no id`() {
		val (_, request) = export(
			"pics",
			"images = true\ncover = true",
			exportInput(projectData = ProjectData(coverImageId = 5), images = listOf(image(1, "Cat", "png", byteArrayOf(1)))),
		)

		assertEquals(listOf(1), request["images"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int })
		assertEquals(JsonNull, request["coverImageId"])
	}

	@Test
	fun `a prose exporter asking for images gets an image block for each image it is sent`() {
		val (exporter, request) = export(
			"pics",
			"input = \"prose\"\nimages = true",
			exportInput(
				scene = "![image](hammer://image/1)\n![image](hammer://image/2)",
				projectData = ProjectData(coverImageId = 1),
				images = listOf(image(1, "Cat", "png", byteArrayOf(1))),
				imageNames = mapOf(1 to "Cat", 2 to "Gone"),
			),
		)

		assertTrue(exporter.embedsImages)
		assertEquals(false, exporter.embedsCover)
		assertTrue(exporter.needsProjectData)
		assertEquals(
			Json.parseToJsonElement("""[[{"type":"image","id":1,"name":"Cat"},{"type":"paragraph","spans":[{"text":"Gone"}]}]]"""),
			request["chapters"]!!.jsonArray[0].jsonObject["prose"],
		)
		assertEquals(JsonNull, request["coverImageId"], "Only an exporter asking for the cover is told its id")
	}

	@Test
	fun `an exporter not asking for images is sent none, even when the input has them`() {
		val (exporter, request) = export(
			"plain",
			null,
			exportInput(
				scene = "![image](hammer://image/1)",
				projectData = ProjectData(coverImageId = 1),
				images = listOf(image(1, "Cat", "png", byteArrayOf(1))),
			),
		)

		assertEquals(false, exporter.embedsImages)
		assertEquals(false, exporter.embedsCover)
		assertTrue(exporter.needsProjectData, "Every plugin exporter is told the project's language")
		assertEquals(JsonArray(emptyList()), request["images"])
		assertEquals(JsonNull, request["coverImageId"])
	}

	@Test
	fun `a prose exporter not asking for images gets an image as its name, even when the input has its file`() {
		val (_, request) = export(
			"plainprose",
			"input = \"prose\"",
			exportInput(
				scene = "![image](hammer://image/1)",
				projectData = ProjectData(coverImageId = 1),
				images = listOf(image(1, "Cat", "png", byteArrayOf(1))),
				imageNames = mapOf(1 to "Cat"),
			),
		)

		assertEquals(
			Json.parseToJsonElement("""[[{"type":"paragraph","spans":[{"text":"Cat"}]}]]"""),
			request["chapters"]!!.jsonArray[0].jsonObject["prose"],
		)
		assertEquals(JsonArray(emptyList()), request["images"])
		assertEquals(JsonNull, request["coverImageId"])
	}

	@Test
	fun `an export whose request cannot fit the plugin's kernel fails before calling it`() {
		startKoin(runtimePlugins())
		val plugin = WasmPlugin(
			PluginManifest.parse(manifest(id = "tight", exporterKeys = "images = true")),
			{ testPlugin("export_echo") },
			maxKernelBytes = 4096,
		)
		val exporter = plugin.exporters().single()

		val error = assertThrows<PluginException> {
			exporter.render(Buffer(), exportInput(images = listOf(image(1, "Cat", "png", ByteArray(4096)))))
		}
		assertTrue("images are too large for export format 'tight.txt'" in error.message!!, error.message)
		assertTrue("at most 4096" in error.message!!, error.message)
		val sink = Buffer()
		exporter.render(sink, exportInput())
		assertEquals("Storm", Json.parseToJsonElement(sink.readUtf8()).jsonObject["projectName"]!!.jsonPrimitive.content)
	}

	// The exporter of the echo plugin [id] declared with [exporterKeys], and the request it sent for [input].
	private fun export(id: String, exporterKeys: String?, input: ExportInput): Pair<StoryExporter, JsonObject> {
		val plugins = runtimePlugins()
		plugins.install(pack(id, manifest(id = id, exporterKeys = exporterKeys)))
		startKoin(plugins)
		val exporter = GlobalContext.get().get<StoryExporterRegistry>().forFormat("$id.txt")
		val sink = Buffer()
		exporter.render(sink, input)
		return exporter to Json.parseToJsonElement(sink.readUtf8()).jsonObject
	}

	private fun image(id: Int, name: String, extension: String, bytes: ByteArray) = ExportImage(id, name, extension, bytes)

	// The exporter of plugin [id], whose module outputs [reply] from every export, or nothing when it is null.
	private fun replyingExporter(
		id: String,
		reply: ByteArray?,
		exporterKeys: String?,
		extension: String = "txt",
		mime: String = "text/plain",
	): StoryExporter {
		val plugins = runtimePlugins()
		val manifest = manifest(id = id, format = "$id.$extension", extension = extension, mime = mime, exporterKeys = exporterKeys)
		plugins.install(pack(id, manifest, module = "reply", binaries = listOfNotNull(reply?.let { "resources/reply.json" to it }).toMap()))
		startKoin(plugins)
		return GlobalContext.get().get<StoryExporterRegistry>().forFormat("$id.$extension")
	}

	private fun archiveExporter(reply: String?, exporterKeys: String = "") = replyingExporter(
		"zipped",
		reply?.encodeToByteArray(),
		"output = \"archive\"\n$exporterKeys",
		extension = "zip",
		mime = "application/zip",
	)

	private fun unzip(bytes: ByteArray): List<Pair<String, ByteArray>> = ZipInputStream(bytes.inputStream()).use { zip ->
		generateSequence { zip.nextEntry }.map { it.name to zip.readBytes() }.toList()
	}

	private fun StoryExporter.renderFiles(input: ExportInput = exportInput()): List<Pair<String, ByteArray>> {
		val sink = Buffer()
		render(sink, input)
		return unzip(sink.readByteArray())
	}

	// The message an archive exporter declared with [exporterKeys] and replying [reply] fails with for
	// [input], having written nothing.
	private fun archiveFailure(reply: String?, exporterKeys: String = "", input: ExportInput = exportInput()): String {
		GlobalContext.stopKoin()
		val exporter = archiveExporter(reply, exporterKeys)
		val sink = Buffer()
		val error = assertThrows<PluginException>("$reply") { exporter.render(sink, input) }
		assertEquals(0L, sink.size, "$reply")
		return error.message!!
	}

	@Test
	fun `an archive exporter's files are zipped in order, text as UTF-8 and content decoded`() {
		val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, -1)
		val content = java.util.Base64.getEncoder().encodeToString(png)
		val exporter = archiveExporter(
			"""{"files": [{"path": "story.md", "text": "# Café über ✓"}, {"path": "images/carte été.png", "content": "$content"}]}""",
		)
		assertEquals("zip" to "application/zip", exporter.fileExtension to exporter.mimeType)

		val files = exporter.renderFiles()

		assertEquals(listOf("story.md", "images/carte été.png"), files.map { it.first })
		assertContentEquals("# Café über ✓".encodeToByteArray(), files[0].second)
		assertContentEquals(png, files[1].second)
	}

	@Test
	fun `an archive file with empty text or content is empty, and a null text is no text`() {
		val exporter = archiveExporter(
			"""{"files": [{"path": "a.txt", "text": ""}, {"path": "b.bin", "content": ""}, {"path": "c.bin", "text": null, "content": "eA=="}]}""",
		)
		val files = exporter.renderFiles()

		assertEquals(listOf("a.txt", "b.bin", "c.bin"), files.map { it.first })
		assertEquals(listOf(0, 0), files.take(2).map { it.second.size })
		assertContentEquals("x".encodeToByteArray(), files[2].second)
	}

	@Test
	fun `an archive path of 65535 UTF-8 bytes is accepted`() {
		val path = "é".repeat(32767) + "a"
		val files = archiveExporter("""{"files": [{"path": "$path", "text": "x"}]}""").renderFiles()
		assertEquals(listOf(path), files.map { it.first })
	}

	@Test
	fun `an archive exporter may ask for images and the cover`() {
		val exporter = archiveExporter("""{"files": [{"path": "a.txt", "text": "A"}]}""", "images = true\ncover = true")
		assertTrue(exporter.embedsImages)
		assertTrue(exporter.embedsCover)

		val input = exportInput(projectData = ProjectData(coverImageId = 1), images = listOf(image(1, "Cat", "png", byteArrayOf(1))))
		assertEquals(listOf("a.txt"), exporter.renderFiles(input).map { it.first })
	}

	@Test
	fun `an archive file naming a sent image is that image's bytes`() {
		val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, -1)
		val cover = byteArrayOf(-1, -40, -1, 0)
		val exporter = archiveExporter(
			"""{"files": [{"path": "story.md", "text": "![Cat](images/cat.png)"}, {"path": "images/cat.png", "image": 1}, {"path": "cover.jpg", "image": 2}]}""",
			"images = true\ncover = true",
		)
		val input = exportInput(
			projectData = ProjectData(coverImageId = 2),
			images = listOf(image(1, "Cat", "png", png), image(2, "Cover", "jpg", cover)),
		)

		val files = exporter.renderFiles(input)

		assertEquals(listOf("story.md", "images/cat.png", "cover.jpg"), files.map { it.first })
		assertContentEquals(png, files[1].second)
		assertContentEquals(cover, files[2].second)
	}

	@Test
	fun `an archive file naming an image that was not sent fails the export and writes nothing`() {
		val input = exportInput(images = listOf(image(1, "Cat", "png", byteArrayOf(1))))
		// An unknown id, and the id of an input image the exporter did not ask for.
		listOf(9 to "images = true", 1 to "").forEach { (id, keys) ->
			val reply = """{"files": [{"path": "story.md", "text": "x"}, {"path": "images/cat.png", "image": $id}]}"""
			assertEquals(
				"Plugin 'zipped' sent an invalid archive from export format 'zipped.zip': " +
					"Archive path \"images/cat.png\" names image $id, which was not sent",
				archiveFailure(reply, keys, input),
			)
		}
	}

	@Test
	fun `an archive reply that breaks a rule fails the export, naming the path, and writes nothing`() {
		fun text(path: String) = """{"path": "$path", "text": "x"}"""
		fun files(vararg entries: String) = """{"files": [${entries.joinToString()}]}"""
		listOf(
			files(text("")) to "Archive path \"\" is empty",
			files(text("/story.md")) to "Archive path \"/story.md\" starts with /",
			files(text("images\\\\1.png")) to "Archive path \"images\\1.png\" has a backslash",
			files(text("images//1.png")) to "Archive path \"images//1.png\" has an empty segment",
			files(text("images/")) to "Archive path \"images/\" has an empty segment",
			files(text("/")) to "Archive path \"/\" starts with /",
			files(text(".")) to "Archive path \".\" has a . segment",
			files(text("..")) to "Archive path \"..\" has a .. segment",
			files(text("a/..")) to "Archive path \"a/..\" has a .. segment",
			files(text("./story.md")) to "Archive path \"./story.md\" has a . segment",
			files(text("images/../story.md")) to "Archive path \"images/../story.md\" has a .. segment",
			files(text("é".repeat(32768))) to "Archive path \"${"é".repeat(64)}...\" is longer than 65535 bytes",
			files(text("a\\ud800"), text("a\\udbff")) to "Archive path \"a\ud800\" is not valid Unicode",
			files(text("story.md"), text("story.md")) to "Archive path \"story.md\" is already in the archive",
			files("""{"path": "a", "text": "x", "content": "eA=="}""") to "Archive path \"a\" has more than one of text, content and image",
			files("""{"path": "a", "text": "x", "image": 1}""") to "Archive path \"a\" has more than one of text, content and image",
			files("""{"path": "a", "content": "eA==", "image": 1}""") to "Archive path \"a\" has more than one of text, content and image",
			files("""{"path": "a"}""") to "Archive path \"a\" has none of text, content and image",
			files("""{"path": "a", "text": null, "image": null}""") to "Archive path \"a\" has none of text, content and image",
			files("""{"path": "a", "content": "not base64!"}""") to "Archive path \"a\" has content that is not standard padded base64",
			files("""{"path": "a", "content": "eA"}""") to "Archive path \"a\" has content that is not standard padded base64",
			files("""{"path": "a", "content": "eA==\n"}""") to "Archive path \"a\" has content that is not standard padded base64",
			files("""{"path": "a", "content": "e A=="}""") to "Archive path \"a\" has content that is not standard padded base64",
			files("""{"path": "a", "content": "-_8="}""") to "Archive path \"a\" has content that is not standard padded base64",
			files() to "An archive needs at least one file",
		).forEach { (reply, problem) ->
			assertEquals("Plugin 'zipped' sent an invalid archive from export format 'zipped.zip': $problem", archiveFailure(reply))
		}
	}

	@Test
	fun `an archive reply that is not one fails the export and writes nothing`() {
		val input = exportInput(images = listOf(image(1, "Cat", "png", byteArrayOf(1))))
		listOf(
			null,
			"done",
			"[1, 2]",
			"{}",
			"""{"files": "story.md"}""",
			"""{"files": [{"text": "x"}]}""",
			"""{"files": [{"path": "a", "image": "1"}]}""",
			"""{"files": [{"path": "a", "image": 1.5}]}""",
			"""{"files": [{"path": "a", "image": true}]}""",
			"""{"files": [{"path": "a", "image": [1]}]}""",
			"""{"files": [{"path": "a", "image": {}}]}""",
		).forEach { reply ->
			val message = archiveFailure(reply, "images = true", input)
			assertTrue(message.startsWith("Plugin 'zipped' sent a malformed reply from export format 'zipped.zip': "), message)
		}
		assertEquals(
			"Plugin 'zipped' sent a malformed reply from export format 'zipped.zip': An archive image is an integer id, not \"1\"",
			archiveFailure("""{"files": [{"path": "a", "image": "1"}]}""", "images = true", input),
		)
	}

	@Test
	fun `a file exporter's reply is the file, byte for byte`() {
		val reply = """{"files": [{"path": "a", "text": "x"}]}""".encodeToByteArray() + byteArrayOf(0, -1, -2, 80, 75, 3, 4)
		listOf(null, "output = \"file\"").forEach { keys ->
			GlobalContext.stopKoin()
			val sink = Buffer()
			replyingExporter("raw", reply, keys).render(sink, exportInput())
			assertContentEquals(reply, sink.readByteArray(), keys)
		}
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
		assertEquals(false, action.keep)
		assertEquals(setOf(ActionPlace.Project), action.places)
		val request = runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))) }.page!!.state!!.jsonObject

		assertEquals(
			setOf("action", "project", "settings", "locale", "context", "input", "button", "changed", "state", "resumed"),
			request.keys,
		)
		assertEquals("report", request["action"]!!.jsonPrimitive.content)
		assertEquals("Storm", request["project"]!!.jsonPrimitive.content)
		assertEquals(JsonPrimitive(true), request["settings"]!!.jsonObject["shout"])
		assertEquals(JsonObject(emptyMap()), request["input"])
		assertEquals(Json.parseToJsonElement("""{"place":"project","id":null}"""), request["context"])
		listOf("locale", "button", "changed", "state").forEach { key -> assertEquals(JsonNull, request[key], "$key") }
		assertEquals(JsonPrimitive(false), request["resumed"])
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
		val state = Json.parseToJsonElement("""{"step":2,"answers":["Mara"]}""")
		val call = ActionCall("Storm", ActionPlace.Entry, 7, input, button = "again", state = state, resumed = true)
		val request = runBlocking { loaded.run(call) }.page!!.state!!.jsonObject
		assertEquals(input, request["input"])
		assertEquals(Json.parseToJsonElement("""{"place":"entry","id":7}"""), request["context"])
		assertEquals("again", request["button"]!!.jsonPrimitive.content)
		assertEquals(state, request["state"])
		assertEquals(JsonPrimitive(true), request["resumed"])
	}

	@Test
	fun `an action that keeps its runs says so, and its first page may ask for text`() {
		val plugins = runtimePlugins()
		val action = """
			keep = true

			[[actions.field]]
			key = "name"
			type = "text"
			label = "Name"
			required = true

			[[actions.field]]
			key = "story"
			type = "text"
			label = "Story"
			format = "rich-extended"
			default = "It *began*."
		""".trimIndent()
		plugins.install(pack("echo", manifest(action = "report") + "\n" + action))

		val loaded = startKoin(plugins).plugins.single().actions().single()
		assertEquals(true, loaded.keep)
		assertEquals("1.0.0", loaded.pluginVersion)
		val (name, story) = loaded.fields.map { assertIs<ActionField.Text>(it) }
		assertEquals(listOf(TextFormat.Plain, TextFormat.RichExtended), listOf(name.format, story.format))
		assertEquals(true, name.required)
		assertEquals("It *began*.", story.defaultValue)
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
		assertEquals("de-DE", reply.page!!.state!!.jsonObject["locale"]!!.jsonPrimitive.content)
	}

	@Test
	fun `a translated entity field keeps what it may pick`() {
		val plugins = runtimePlugins(locale = "fr")
		val manifest = manifest(action = "report") + """

			[[actions.field]]
			key = "who"
			type = "entity"
			label = "Who"
			kinds = ["entry", "scene"]
			entry_types = ["person"]
			tags = ["pov"]
			multiple = true
			required = true
			default = [{ kind = "entry", id = 12 }]
		""".trimIndent()
		val french = mapOf("locales/fr.toml" to "[actions.report.fields.who]\nlabel = \"Qui\"")
		plugins.install(pack("echo", manifest, files = french))

		val who = startKoin(plugins).plugins.single().actions().single().fields.single() as ActionField.Entity
		assertEquals("Qui", who.label)
		assertEquals(CatalogFilter(setOf(CatalogKind.Entry, CatalogKind.Scene), setOf(EntryType.PERSON), setOf("pov")), who.filter)
		assertEquals(listOf(true, true), listOf(who.multiple, who.required))
		assertEquals(listOf(CatalogRef(CatalogKind.Entry, 12)), who.defaultRefs)
	}

	private fun described(description: String, languages: String = "\"en\", \"fr-CA\"") =
		manifest().replace("api = 1", "api = 1\ndescription = \"$description\"\nlanguages = [$languages]")

	@Test
	fun `a package's description and languages are shown in the UI's language, with its size and translations`() {
		val plugins = runtimePlugins(locale = "fr")
		val files = mapOf("locales/fr.toml" to "description = \"Répète l'histoire.\"", "locales/de.toml" to "name = \"Echo\"")
		plugins.install(pack("echo", described("Echoes the story."), files = files))

		val installed = plugins.installed().single()
		assertEquals("Répète l'histoire.", installed.manifest?.description)
		assertEquals(listOf("en", "fr-CA"), installed.manifest?.languages)
		assertEquals(setOf("fr", "de"), installed.translations)
		assertEquals(fileSystem.metadata(directory / "packages" / "echo.hammerplugin").size, installed.size)
	}

	@Test
	fun `a description too long, or a language that is not a tag, is refused`() {
		val plugins = runtimePlugins()
		assertThrows<PluginPackageException> { plugins.install(pack("long", described("x".repeat(1001)))) }
		assertThrows<PluginPackageException> { plugins.install(pack("tag", described("Fine.", "\"English\""))) }
		val longFrench = mapOf("locales/fr.toml" to "description = \"${"x".repeat(1001)}\"")
		assertThrows<PluginPackageException> { plugins.install(pack("long-french", described("Fine."), files = longFrench)) }
		assertTrue(plugins.installed().isEmpty())
	}

	@Test
	fun `a broken translation, or one not named for a language, is refused`() {
		val plugins = runtimePlugins()
		assertThrows<PluginPackageException> { plugins.install(pack("echo", files = mapOf("locales/fr.toml" to "name = [1, 2"))) }
		assertThrows<PluginPackageException> { plugins.install(pack("echo", files = mapOf("locales/français.toml" to "name = \"Écho\""))) }
	}

	@Test
	fun `a module reads the files in its package's resources, and nothing else`() {
		val plugins = runtimePlugins()
		val resources = mapOf("resources/words/en.txt" to "apple\nbanana", "resources/greeting.txt" to "Hello")
		plugins.install(pack("echo", module = "resource", files = resources))
		startKoin(plugins)
		val plugin = assertIs<WasmPlugin>(plugins.load().single())
		fun read(name: String) = plugin.callBlocking("read", name.encodeToByteArray()).decodeToString()

		assertEquals("apple\nbanana", read("words/en.txt"))
		assertEquals("Hello", read("greeting.txt"))
		assertEquals("", read("missing.txt"))
		assertEquals("", read("manifest.toml"))
		assertEquals("", read("../manifest.toml"))
		assertEquals("", read("words"))
	}

	@Test
	fun `resources past their limits are refused`() {
		val plugins = runtimePlugins()
		val tooLarge = mapOf(
			"resources/a.bin" to ByteArray((PluginPackage.MAX_RESOURCE_BYTES / 2).toInt()),
			"resources/b.bin" to ByteArray((PluginPackage.MAX_RESOURCE_BYTES / 2 + 1).toInt()),
		)
		assertThrows<PluginPackageException> { plugins.install(pack("large", binaries = tooLarge)) }
		val tooMany = (0..1000).associate { "resources/$it.txt" to "$it" }
		assertThrows<PluginPackageException> { plugins.install(pack("many", files = tooMany)) }
		assertTrue(plugins.installed().isEmpty())
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
		refused("[[actions.field]]\nkey = \"a\"\ntype = \"string\"\nlabel = \"A\"")
		refused("[[actions.field]]\nkey = \"a\"\ntype = \"text\"\nlabel = \"A\"\nformat = \"html\"")
	}

	private fun replying(reply: String?) = runtimePlugins().let { plugins ->
		plugins.install(pack("names", manifest(id = "names", action = "report"), module = "reply", files = listOfNotNull(reply?.let { "resources/reply.json" to it }).toMap()))
		startKoin(plugins).plugins.single().actions().single()
	}

	private val call = ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()))

	@Test
	fun `a page reply is its title, markdown, fields, buttons, and state, with a message`() {
		val reply = runBlocking {
			replying(
				"""
				{"page": {"title": "Names", "markdown": "# Names",
				  "fields": [{"key": "note", "type": "text", "format": "rich", "label": "Note", "default": "*Hi*"},
				             {"key": "count", "type": "int", "label": "How many", "min": 1, "max": 9, "default": 3}],
				  "buttons": [{"id": "add:0", "label": "Aldric", "inline": true},
				              {"id": "back", "label": "Back", "validate": false},
				              {"id": "more", "label": "More names", "primary": true}],
				  "state": {"seed": 7, "added": [0]}},
				 "message": "Made", "extra": "ignored"}
				""",
			).run(call)
		}
		val page = reply.page!!
		assertEquals(listOf("Names", "# Names", "Made"), listOf(page.title, page.markdown, reply.message))
		val note = assertIs<ActionField.Text>(page.fields.first())
		assertEquals(TextFormat.Rich to JsonPrimitive("*Hi*"), note.format to note.default)
		assertEquals(JsonPrimitive(3L), page.fields.last().default)
		assertEquals(
			listOf(Triple("add:0", true, true), Triple("back", false, false), Triple("more", false, true)),
			page.buttons.map { Triple(it.id, it.inline, it.validate) },
		)
		assertEquals(listOf(false, false, true), page.buttons.map { it.primary })
		assertEquals(Json.parseToJsonElement("""{"seed":7,"added":[0]}"""), page.state)
		assertTrue(reply.errors.isEmpty())
		assertNull(reply.open)
	}

	@Test
	fun `errors and open come through, and empty output ends the run`() {
		val errors = runBlocking { replying("""{"errors": {"name": "Taken", "": "Try again"}, "open": {"place": "entry", "id": 12}}""").run(call) }
		assertEquals(mapOf("name" to "Taken", "" to "Try again"), errors.errors)
		assertEquals(ActionPlace.Entry to 12, errors.open!!.place to errors.open!!.id)
		assertNull(errors.page)

		GlobalContext.stopKoin()
		val home = runBlocking { replying("""{"open": {"place": "project"}}""").run(call) }
		assertEquals(ActionPlace.Project to null, home.open!!.place to home.open!!.id)

		listOf(null, "", "  \n", "{}").forEach { output ->
			GlobalContext.stopKoin()
			val ended = runBlocking { replying(output).run(call) }
			assertTrue(ended.page == null && ended.open == null && ended.errors.isEmpty() && ended.message == null, "$output")
		}
	}

	@Test
	fun `a reply that is not one fails the call`() {
		listOf(
			"done",
			"[1, 2]",
			"""{"page": {"fields": [{"key": "a", "type": "colour", "label": "A"}]}}""",
			"""{"page": {"fields": [{"key": "a", "type": "choice", "label": "A"}]}}""",
			"""{"page": {"buttons": [{"label": "No id"}]}}""",
			"""{"open": {"place": "sidebar", "id": 1}}""",
			"""{"errors": {"a": 1}}""",
		).forEach { output ->
			GlobalContext.stopKoin()
			assertThrows<PluginException>(output) { runBlocking { replying(output).run(call) } }
		}
	}

	@Test
	fun `a page's state may hold up to 1 MiB`() {
		fun withState(bytes: Int) = """{"page": {"state": "${"x".repeat(bytes - 2)}"}}"""
		val fits = runBlocking { replying(withState(WasmPlugin.MAX_STATE_BYTES)).run(call) }
		assertEquals(WasmPlugin.MAX_STATE_BYTES - 2, fits.page!!.state!!.jsonPrimitive.content.length)

		GlobalContext.stopKoin()
		assertThrows<PluginException> { runBlocking { replying(withState(WasmPlugin.MAX_STATE_BYTES + 1)).run(call) } }
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
	fun `cancelling an action stops the plugin at once, between its progress reports`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest(action = "report"), module = "action_spin"))
		val action = startKoin(plugins, io = Dispatchers.IO).plugins.single().actions().single()

		runBlocking {
			val started = CompletableDeferred<Unit>()
			val call = ActionCall("Storm", ActionPlace.Project, null, JsonObject(emptyMap()), onProgress = { started.complete(Unit) })
			val run = launch(Dispatchers.Default) { action.run(call) }
			started.await()
			val stopping = measureTime { run.cancelAndJoin() }
			assertTrue(stopping < 2.seconds, "took $stopping")
		}
	}

	@Test
	fun `a diagnostics check keeps only issues inside the paragraphs, at UTF-16 offsets`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest() + diagnostics("grammar"), module = "diagnose"))

		val check = startKoin(plugins).plugins.single().textDiagnostics().single()
		val found = runBlocking { check.diagnose(TextDiagnosticsRequest(listOf("Ét the the end", "Fine."), "en", "Project")) }

		assertEquals("Grammar", check.label)
		assertEquals(2, found.size)
		val issue = found[0].single()
		assertEquals(listOf(3, 10, "Repeated word"), listOf(issue.start, issue.end, issue.message))
		assertEquals(listOf("the" to "the", "" to "Remove the repeat"), issue.fixes.map { it.replacement to it.label })
		assertEquals(TextDiagnosticSeverity.Suggestion, issue.severity)
		assertTrue(found[1].isEmpty())
	}

	@Test
	fun `a diagnostics check gets whole scenes only when its manifest asks for them`() {
		val plugins = runtimePlugins()
		plugins.install(pack("echo", manifest() + diagnostics("grammar") + diagnostics("echoes", scope = "scene")))

		val checks = startKoin(plugins).plugins.single().textDiagnostics()

		assertEquals(listOf(false, true), checks.map { it.wholeScene })
		assertThrows<PluginPackageException> {
			plugins.install(pack("odd", manifest(id = "odd") + diagnostics("grammar", scope = "chapter")))
		}
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

		assertEquals(listOf(emptyList(), emptyList()), runBlocking { check.diagnose(TextDiagnosticsRequest(listOf("One.", "Two."), null, "Project")) }.map { it.toList() })
	}

	private fun userHostsManifest(id: String, toggle: String? = null) = """
		id = "$id"
		name = "Fetch"
		version = "1.0.0"
		api = 1

		[permissions.userHosts]
		setting = "server"
	""".trimIndent() + toggle?.let { "\ntoggle = \"$it\"" }.orEmpty()

	@Test
	fun `a userHosts setting with a default, or a toggle on by default, is refused`() {
		val plugins = runtimePlugins()
		fun server(type: String, default: String? = null) =
			"[[setting]]\nkey = \"server\"\ntype = \"$type\"\nlabel = \"Server\"" + default?.let { "\ndefault = \"$it\"" }.orEmpty()
		val toggleOn = "\n\n[[setting]]\nkey = \"allowServer\"\ntype = \"bool\"\nlabel = \"Allow\"\ndanger = true\ndefault = true"

		val error = assertThrows<PluginPackageException> {
			plugins.inspect(pack("exfil", userHostsManifest("exfil"), settingsToml = server("string", "http://exfil.attacker.tld")))
		}
		assertTrue("default" in error.message!!, error.message)
		assertThrows<PluginPackageException> {
			plugins.inspect(pack("on", userHostsManifest("on", toggle = "allowServer"), settingsToml = server("string") + toggleOn))
		}
		plugins.inspect(pack("text", userHostsManifest("text"), settingsToml = server("string")))
		plugins.inspect(pack("secret", userHostsManifest("secret"), settingsToml = server("secret")))
	}

	private fun fetching(sent: MutableList<String>, vararg settings: SettingDeclaration, toggle: String? = null): WasmPlugin {
		val registry = startKoin(runtimePlugins())
		// Built directly, since a package declaring these is refused.
		val plugin = WasmPlugin(
			PluginManifest.parse(userHostsManifest("fetch", toggle)),
			{ testPlugin("fetch") },
			settings.toList(),
			httpClient = PluginHttp { request -> sent += request.url; PluginHttpResponse(200, "ok".encodeToByteArray()) },
		)
		registry.add(plugin)
		return plugin
	}

	private fun WasmPlugin.fetch(url: String) = callBlocking("fetch", """{"url":"$url"}""".encodeToByteArray()).decodeToString()

	@Test
	fun `the host a userHosts setting names is reachable only once the user enters it, whatever its default`() {
		val sent = mutableListOf<String>()
		val plugin = fetching(sent, SettingDeclaration.Text("server", "Server", defaultValue = "http://exfil.attacker.tld"))

		assertThrows<PluginException> { plugin.fetch("http://exfil.attacker.tld/check") }
		assertEquals(emptyList(), sent)

		GlobalContext.get().get<PluginRegistry>().settings("fetch")!!.set("server", JsonPrimitive("http://192.168.1.20:8081"))
		assertEquals("ok", plugin.fetch("http://192.168.1.20:8081/v2/check"))
		assertEquals(listOf("http://192.168.1.20:8081/v2/check"), sent)
	}

	@Test
	fun `a userHosts toggle on only by default does not open the host`() {
		val sent = mutableListOf<String>()
		val plugin = fetching(
			sent,
			SettingDeclaration.Text("server", "Server"),
			SettingDeclaration.Toggle("allowServer", "Allow", defaultValue = true, danger = true),
			toggle = "allowServer",
		)
		GlobalContext.get().get<PluginRegistry>().settings("fetch")!!.set("server", JsonPrimitive("http://192.168.1.20:8081"))

		assertThrows<PluginException> { plugin.fetch("http://192.168.1.20:8081/v2/check") }
		assertEquals(emptyList(), sent)
	}

	/** How many connections a loopback server, at the port [block] is given, accepted while it ran. */
	private fun loopbackConnections(block: (port: Int) -> Unit): Int =
		ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
			var connections = 0
			val listener = thread {
				try {
					while (true) server.accept().use { connections++ }
				} catch (e: SocketException) {
					// Closed once the block is done.
				}
			}
			block(server.localPort)
			server.close()
			listener.join(5_000)
			connections
		}

	@Test
	fun `inspecting a package runs its initializer without network`() {
		val connections = loopbackConnections { port ->
			val manifest = "id = \"fetch\"\nname = \"Fetch\"\nversion = \"1.0.0\"\napi = 1\n\n[permissions]\nhttp = [\"127.0.0.1\"]"
			val request = mapOf("resources/request" to """{"url":"https://127.0.0.1:$port/"}""")

			assertThrows<PluginPackageException> {
				runtimePlugins().inspect(pack("fetch", manifest, module = "fetch", settingsToml = null, files = request))
			}
		}

		assertEquals(0, connections)
	}

	@Test
	fun `a default from an earlier version does not open the host once an update names its setting in userHosts`() {
		val connections = loopbackConnections { port ->
			val plugins = runtimePlugins()
			val v1 = "id = \"fetch\"\nname = \"Fetch\"\nversion = \"1.0.0\"\napi = 1"
			val server = "[[setting]]\nkey = \"server\"\ntype = \"string\"\nlabel = \"Server\""
			plugins.install(pack("v1", v1, module = "fetch", settingsToml = "$server\ndefault = \"http://127.0.0.1:$port\"\n\n$settings"))
			val registry = startKoin(plugins)
			registry.settings("fetch")!!.set("shout", JsonPrimitive(true))

			plugins.install(pack("v2", userHostsManifest("fetch"), module = "fetch", settingsToml = "$server\n\n$settings"))

			val plugin = registry.plugins.single() as WasmPlugin
			assertThrows<PluginException> { plugin.fetch("http://127.0.0.1:$port/") }
		}

		assertEquals(0, connections)
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

	private fun startKoin(plugins: RuntimePlugins, io: CoroutineContext = Dispatchers.Unconfined): PluginRegistry {
		val registry = PluginRegistry().also(plugins::activate)
		val greet = operation<Greeting, Greeting>("greet", "", Access.Read, OperationScope.Content) { it }
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { createTomlSerializer() }
			single<CoroutineContext>(named(DISPATCHER_IO)) { io }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
			single { StoryExporterRegistry(getAll(), getAll()) }
		}
		GlobalContext.startKoin {
			modules(listOf(base) + listOf(registry.koinModule()) + module {
				single { OperationRegistry(listOf(greet), NoProjects, ::ProjectSyncGateRepository) }
			})
		}
		return registry
	}

	private fun exportInput(
		scene: String = "It rained.",
		projectData: ProjectData? = null,
		images: List<ExportImage> = emptyList(),
		imageNames: Map<Int, String> = images.associate { it.id to it.name },
	) = ExportInput(
		projectName = "Storm",
		projectData = projectData,
		chapters = listOf(StoryChapter("Chapter One", listOf(scene))),
		treatTopLevelAsChapters = true,
		options = ExportOptions(),
		language = "en",
		strings = ExportStrings(
			contentsTitle = "Contents",
			authorByline = null,
			wordCount = "about 12,500 words",
			endMarker = "END",
		),
		images = images.associateBy { it.id },
		imageNames = imageNames,
	)

	@Serializable
	data class Greeting(val name: String)

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
