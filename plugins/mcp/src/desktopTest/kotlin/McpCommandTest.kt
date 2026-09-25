import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.plugins.mcp.McpCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
			OperationDescriptor("sync.run", "Sync.", Access.Write, agentVisible = false, schema, JsonObject(emptyMap())),
		)
	}

	@Test
	fun `refuses to serve until enabled`() = runBlocking {
		val stderr = Buffer()
		val code = McpCommand { false }.run(emptyList(), CliIo(Buffer(), Buffer(), stderr), dispatcher)

		assertEquals(McpCommand.DISABLED, code)
		assertTrue("Settings" in stderr.readUtf8())
	}

	@Test
	fun `serves agent-visible operations as tools`() = runBlocking {
		val toServer = PipedOutputStream()
		val fromServer = PipedInputStream()
		val io = CliIo(PipedInputStream(toServer).source().buffer(), PipedOutputStream(fromServer).sink().buffer(), Buffer())
		val responses = fromServer.bufferedReader()
		fun send(message: String) = toServer.write("$message\n".toByteArray()).also { toServer.flush() }
		suspend fun receive(): JsonObject =
			withTimeout(10.seconds) { withContext(Dispatchers.IO) { Json.parseToJsonElement(responses.readLine()).jsonObject } }

		val server = async(Dispatchers.Default) { McpCommand { true }.run(emptyList(), io, dispatcher) }

		send("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")
		assertEquals("hammer", receive()["result"]!!.jsonObject["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
		send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")

		send("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
		val tools = receive()["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
		assertEquals(listOf("scene_read"), tools.map { it["name"]!!.jsonPrimitive.content })

		send("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"scene_read","arguments":{"project":"Storm","id":1}}}""")
		val result = receive()["result"]!!.jsonObject
		assertTrue("The storm came early." in result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
		assertEquals("scene.read", dispatcher.calls.single().first)

		send("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"scene_read","arguments":{"project":"Storm","id":404}}}""")
		val failed = receive()["result"]!!.jsonObject
		assertEquals(JsonPrimitive(true), failed["isError"])
		assertTrue("No scene 404" in failed["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)

		toServer.close()
		assertEquals(0, withTimeout(10.seconds) { server.await() })
	}
}
