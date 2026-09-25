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
	private fun serve(
		vararg messages: String,
		changes: Boolean = false,
		deletes: Boolean = false,
		granted: List<String>? = null,
	): Map<Int, JsonObject> {
		val built = File(System.getenv("HAMMER_PLUGINS"), "kotlin/mcp/build/mcp.hammerplugin")
		check(built.exists()) { "Run kotlin/build.sh in hammer-plugins first" }
		val download = "/downloads/mcp.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, directory, cacheDirectory)
		plugins.install(download)
		if (granted != null) {
			val list = granted.joinToString(", ") { "\"$it\"" }
			fileSystem.write(directory / "_runtime-plugins.toml") { writeUtf8("[plugins.mcp]\nenabled = true\ngranted = [$list]\n") }
		}
		fileSystem.write(directory / "mcp.toml") { writeUtf8("changes = $changes\ndeletes = $deletes\n") }

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

	/** The tools offered with these settings, by name. */
	private fun tools(changes: Boolean = false, deletes: Boolean = false, granted: List<String>? = null): Map<String, JsonObject> =
		serve(request(1, "tools/list"), changes = changes, deletes = deletes, granted = granted).getValue(1).result()["tools"]!!
			.jsonArray.map { it.jsonObject }.associateBy { it["name"]!!.jsonPrimitive.content }

	@Test
	fun `tools read unless changes are allowed, and never write prose`() {
		val reading = tools().keys
		assertTrue("project_list" in reading && "scene_read" in reading && "search" in reading)
		assertFalse("note_create" in reading || "scene_create" in reading)
		assertFalse(reading.any { it.startsWith("account_") || it.startsWith("sync_") })
		assertFalse("ops_list" in reading)

		val changing = tools(changes = true, deletes = true).keys
		assertTrue(listOf("note_create", "entry_update", "scene_create", "scene_meta_write", "timeline_move").all { it in changing })
		assertFalse(listOf("scene_write", "scene_append", "draft_apply", "project_import", "project_delete").any { it in changing })
	}

	@Test
	fun `a grant the manifest no longer requests gives nothing, so an older install cannot write prose`() {
		val tools = tools(changes = true, granted = listOf("content:read", "content:write")).keys
		assertTrue("scene_read" in tools)
		assertFalse(listOf("scene_write", "scene_append", "draft_apply", "note_create").any { it in tools })
	}

	@Test
	fun `deletes are tools only when allowed, and never a project's`() {
		assertEquals(emptyList(), tools(deletes = false).keys.filter { it.endsWith("_delete") })
		val allowed = tools(deletes = true)
		assertEquals(
			setOf("scene_delete", "draft_delete", "note_delete", "entry_delete", "timeline_delete", "idea_delete"),
			allowed.keys.filter { it.endsWith("_delete") }.toSet(),
		)
		val hints = allowed.getValue("note_delete")["annotations"]!!.jsonObject
		assertEquals(JsonPrimitive(true), hints["destructiveHint"])
		assertEquals(-32602, serve(call(1, "note_delete", "{}")).getValue(1)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
	}

	@Test
	fun `tool calls run the operation and report its output or failure`() {
		val replies = serve(
			call(1, "project_list", "{}"),
			call(2, "note_create", """{"project":"Storm","content":"Rain."}"""),
			call(3, "scene_read", """{"project":"Missing","id":1}"""),
			call(4, "no_such_tool", "{}"),
		)

		val listed = replies.getValue(1).result()
		assertEquals(JsonPrimitive(false), listed["isError"])
		assertEquals("""{"projects":[]}""", listed["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
		assertEquals(-32602, replies.getValue(2)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
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
