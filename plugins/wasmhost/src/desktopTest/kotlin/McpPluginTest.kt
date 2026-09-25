import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.core.descriptor
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The MCP plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout. */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class McpPluginTest {

	private val fileSystem = FakeFileSystem()
	private val directory = "/config/plugins".toPath()
	private val cacheDirectory = "/cache/plugins".toPath()
	private val dispatcher = RecordingDispatcher()

	/** The replies to [messages], one line each, by the id they answer. */
	private fun serve(vararg messages: String, liveEdits: Boolean = false): Map<Int, JsonObject> {
		val built = File(System.getenv("HAMMER_PLUGINS"), "kotlin/mcp/build/mcp.hammerplugin")
		check(built.exists()) { "Run kotlin/build.sh in hammer-plugins first" }
		val download = "/downloads/mcp.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, directory, cacheDirectory)
		plugins.install(download)
		fileSystem.write(directory / "mcp.toml") { writeUtf8("liveEdits = $liveEdits\n") }

		val stdout = Buffer()
		val io = CliIo(Buffer().writeUtf8(messages.joinToString("\n")), stdout, Buffer())
		runBlocking { plugins.load().single().cliCommands().single().run(emptyList(), io, dispatcher) }
		return stdout.readUtf8().lines().filter { it.isNotEmpty() }
			.map { Json.parseToJsonElement(it).jsonObject }
			.associateBy { it["id"]!!.jsonPrimitive.content.toInt() }
	}

	private fun request(id: Int, method: String, params: String = "{}") =
		"""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}"""

	private fun call(id: Int, tool: String, arguments: String) =
		request(id, "tools/call", """{"name":"$tool","arguments":$arguments}""")

	private fun JsonObject.result() = this["result"]!!.jsonObject

	@Test
	fun `it initializes, answers pings, and ignores notifications`() {
		val replies = serve(
			request(1, "initialize", """{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"1"}}"""),
			"""{"jsonrpc":"2.0","method":"notifications/initialized"}""",
			request(2, "ping"),
			request(3, "resources/list"),
		)

		assertEquals(setOf(1, 2, 3), replies.keys)
		assertEquals("2025-03-26", replies.getValue(1).result()["protocolVersion"]!!.jsonPrimitive.content)
		assertEquals("hammer", replies.getValue(1).result()["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
		assertEquals(JsonObject(emptyMap()), replies.getValue(2).result())
		assertEquals(-32601, replies.getValue(3)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
	}

	@Test
	fun `tools are the granted operations, without live edits unless allowed`() {
		val tools = serve(request(1, "tools/list")).getValue(1).result()["tools"]!!.jsonArray.map { it.jsonObject }
		val names = tools.map { it["name"]!!.jsonPrimitive.content }

		assertTrue("project_list" in names && "scene_write" in names && "ops_list" in names)
		assertFalse(names.any { it.startsWith("account_") || it.startsWith("sync_") })
		assertFalse("scene_delete" in names)
		assertFalse("scene_append" in names)
		val mode = tools.single { it["name"]!!.jsonPrimitive.content == "scene_write" }["inputSchema"]!!
			.jsonObject["properties"]!!.jsonObject["mode"]!!.jsonObject["enum"]!!.jsonArray
		assertFalse(JsonPrimitive("live") in mode)

		val live = serve(request(1, "tools/list"), liveEdits = true).getValue(1).result()["tools"]!!.jsonArray
		assertTrue(live.any { it.jsonObject["name"]!!.jsonPrimitive.content == "scene_append" })
	}

	@Test
	fun `tool calls run the operation and report its output or failure`() {
		val replies = serve(
			call(1, "project_list", "{}"),
			call(2, "scene_write", """{"project":"Storm","id":1,"markdown":"Rain.","mode":"live"}"""),
			call(3, "scene_read", """{"project":"Missing","id":1}"""),
			call(4, "no_such_tool", "{}"),
		)

		val listed = replies.getValue(1).result()
		assertEquals(JsonPrimitive(false), listed["isError"])
		assertEquals("""{"projects":[]}""", listed["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
		assertEquals(JsonPrimitive(true), replies.getValue(2).result()["isError"])
		assertEquals(JsonPrimitive(true), replies.getValue(3).result()["isError"])
		assertEquals(-32602, replies.getValue(4)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
		assertEquals(listOf("project.list", "scene.read"), dispatcher.dispatched)
	}

	@Test
	fun `text of every UTF-8 width passes through intact`() {
		// Each width of character at each offset within the kit's 8-byte reads.
		val names = listOf("é", "“", "中", "😀").flatMap { c -> (0 until 8).map { "x".repeat(it) + c + "y".repeat(9) } }
		dispatcher.projects = names
		val note = names.joinToString(" ")

		val listed = serve(call(1, "project_list", """{"note":"$note"}""")).getValue(1).result()

		assertEquals(note, dispatcher.inputs.single().jsonObject["note"]!!.jsonPrimitive.content)
		val text = listed["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
		assertEquals(names, Json.parseToJsonElement(text).jsonObject["projects"]!!.jsonArray.map { it.jsonPrimitive.content })
	}

	/** Hammer's own operations and schemas; every operation but project.list fails as not found. */
	private class RecordingDispatcher : Dispatcher {
		val dispatched = mutableListOf<String>()
		val inputs = mutableListOf<JsonElement>()
		var projects = emptyList<String>()
		private val operations = coreOperations().map { it.descriptor() }

		override suspend fun operations(): List<OperationDescriptor> = operations

		override suspend fun dispatch(operation: String, input: JsonElement): JsonElement {
			dispatched += operation
			inputs += input
			return when (operation) {
				"project.list" -> buildJsonObject { put("projects", JsonArray(projects.map(::JsonPrimitive))) }
				else -> notFound("No project 'Missing'")
			}
		}
	}
}
