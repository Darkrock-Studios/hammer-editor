import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.LIVE_EDIT_KEY
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.plugins.mcp.McpCommand
import com.darkrockstudios.apps.hammer.plugins.mcp.McpSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import okio.buffer
import okio.sink
import okio.source
import org.junit.jupiter.api.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpCommandTest {

	private val schema = Json.parseToJsonElement(
		"""{"type":"object","properties":{"project":{"type":"string"},"id":{"type":"integer"}},"required":["project","id"]}"""
	).jsonObject

	private val dispatcher = object : Dispatcher {
		val calls = mutableListOf<Pair<String, JsonElement>>()

		override suspend fun dispatch(operation: String, input: JsonElement): JsonElement {
			calls += operation to input
			if (input.jsonObject["id"] == JsonPrimitive(404)) {
				throw OperationException(OperationException.Kind.NotFound, "No scene 404")
			}
			return buildJsonObject { put("markdown", "The storm came early.") }
		}

		override suspend fun operations() = listOf(
			OperationDescriptor("scene.read", "A scene's markdown.", Access.Read, agentVisible = true, schema, JsonObject(emptyMap())),
			OperationDescriptor("scene.write", "Replace a scene's text.", Access.Write, agentVisible = true, writeSchema, JsonObject(emptyMap())),
			OperationDescriptor("scene.append", "Add to a scene.", Access.Write, agentVisible = true, appendSchema, JsonObject(emptyMap())),
			OperationDescriptor("sync.run", "Sync.", Access.Write, agentVisible = false, schema, JsonObject(emptyMap())),
		)
	}

	private val writeSchema = Json.parseToJsonElement(
		"""{"type":"object","properties":{"id":{"type":"integer"},"mode":{"type":"string","enum":["draft","live"],"$LIVE_EDIT_KEY":["live"]}},"required":["id","mode"]}"""
	).jsonObject

	private val appendSchema = JsonObject(schema + (LIVE_EDIT_KEY to JsonPrimitive(true)))

	/** An MCP client talking to a running `hammer mcp`. */
	private inner class Session(settings: McpSettings, scope: CoroutineScope) {
		private val toServer = PipedOutputStream()
		private val fromServer = PipedInputStream()
		private val responses = fromServer.bufferedReader()
		private val io = CliIo(PipedInputStream(toServer).source().buffer(), PipedOutputStream(fromServer).sink().buffer(), Buffer())
		private val server = scope.async(Dispatchers.Default) { McpCommand { settings }.run(emptyList(), io, dispatcher) }
		private var nextId = 1

		fun send(message: String) = toServer.write("$message\n".toByteArray()).also { toServer.flush() }

		suspend fun receive(): JsonObject =
			withTimeout(10.seconds) { withContext(Dispatchers.IO) { Json.parseToJsonElement(responses.readLine()).jsonObject } }

		suspend fun request(method: String, params: String = "{}"): JsonObject {
			send("""{"jsonrpc":"2.0","id":${nextId++},"method":"$method","params":$params}""")
			return receive()["result"]!!.jsonObject
		}

		suspend fun start(): JsonObject {
			val result = request("initialize", """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}""")
			send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
			return result
		}

		suspend fun tools(): Map<String, JsonObject> =
			request("tools/list")["tools"]!!.jsonArray.associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject }

		suspend fun call(tool: String, arguments: String) =
			request("tools/call", """{"name":"$tool","arguments":$arguments}""")

		suspend fun stop(): Int {
			toServer.close()
			return withTimeout(10.seconds) { server.await() }
		}
	}

	private fun JsonObject.text() = this["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

	@Test
	fun `refuses to serve until enabled`() = runBlocking {
		val stderr = Buffer()
		val code = McpCommand { McpSettings(enabled = false, liveEdits = true) }.run(emptyList(), CliIo(Buffer(), Buffer(), stderr), dispatcher)

		assertEquals(McpCommand.DISABLED, code)
		assertTrue("Settings" in stderr.readUtf8())
	}

	@Test
	fun `serves agent-visible operations as tools`() = runBlocking {
		val session = Session(McpSettings(enabled = true, liveEdits = true), this)
		assertEquals("hammer", session.start()["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)

		assertEquals(setOf("scene_read", "scene_write", "scene_append"), session.tools().keys)

		val result = session.call("scene_read", """{"project":"Storm","id":1}""")
		assertTrue("The storm came early." in result.text())
		assertEquals("scene.read", dispatcher.calls.single().first)

		val failed = session.call("scene_read", """{"project":"Storm","id":404}""")
		assertEquals(JsonPrimitive(true), failed["isError"])
		assertTrue("No scene 404" in failed.text())

		assertEquals(0, session.stop())
	}

	@Test
	fun `without live edits, scene changes are drafts only`() = runBlocking {
		val session = Session(McpSettings(enabled = true, liveEdits = false), this)
		session.start()

		val tools = session.tools()
		assertEquals(setOf("scene_read", "scene_write"), tools.keys)
		val mode = tools.getValue("scene_write")["inputSchema"]!!.jsonObject["properties"]!!.jsonObject["mode"]!!.jsonObject
		assertEquals(JsonArray(listOf(JsonPrimitive("draft"))), mode["enum"])
		assertTrue("mode can only be \"draft\"" in tools.getValue("scene_write")["description"]!!.jsonPrimitive.content)

		val refused = session.call("scene_write", """{"id":1,"mode":"live"}""")
		assertEquals(JsonPrimitive(true), refused["isError"])
		assertTrue("Live edits are off" in refused.text())
		assertTrue(dispatcher.calls.isEmpty())

		session.call("scene_write", """{"id":1,"mode":"draft"}""")
		assertEquals("scene.write", dispatcher.calls.single().first)

		assertEquals(0, session.stop())
	}
}
