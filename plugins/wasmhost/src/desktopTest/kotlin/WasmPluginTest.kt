import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OPS_LIST
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WasmPluginTest {

	@Serializable
	data class Greeting(val name: String)

	@Serializable
	data class Reply(val text: String)

	private val greet = operation<Greeting, Reply>("greet", "", Access.Read, OperationScope.Content) {
		if (it.name.isBlank()) notFound("Nobody to greet")
		Reply("Hello, ${it.name}")
	}
	private val wave = operation<Greeting, Reply>("wave", "", Access.Read, OperationScope.Content) { Reply("~") }

	@BeforeEach
	fun setUp() {
		GlobalContext.startKoin {
			modules(module { single { OperationRegistry(listOf(greet, wave), NoProjects) } })
		}
	}

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val plugin = WasmPlugin(
		PluginManifest.parse(
			"""
			id = "echo"
			name = "Echo"
			version = "1.0.0"
			api = 1

			[permissions]
			operations = ["greet"]
			""".trimIndent()
		),
		{ testPlugin("dispatch_echo") },
	)

	private fun dispatch(request: String): JsonObject =
		Json.parseToJsonElement(plugin.callBlocking("run", request.encodeToByteArray()).decodeToString()).jsonObject

	@Test
	fun `permitted operations run`() {
		val reply = dispatch("""{"operation":"greet","input":{"name":"Ada"}}""")

		assertEquals("Hello, Ada", reply["output"]!!.jsonObject["text"]!!.jsonPrimitive.content)
	}

	@Test
	fun `operations the manifest does not list are refused`() {
		val reply = dispatch("""{"operation":"wave","input":{"name":"Ada"}}""")

		assertEquals(WasmPlugin.PERMISSION_DENIED, reply.errorKind())
	}

	@Test
	fun `operation failures come back as errors`() {
		assertEquals("NotFound", dispatch("""{"operation":"greet","input":{"name":""}}""").errorKind())
		assertEquals("InvalidInput", dispatch("""{"operation":"greet","input":{}}""").errorKind())
	}

	@Test
	fun `malformed requests come back as invalid input`() {
		assertEquals("InvalidInput", dispatch("not json").errorKind())
	}

	@Test
	fun `a module cannot re-enter itself`() {
		GlobalContext.stopKoin()
		val reenter = operation<Greeting, Reply>("greet", "", Access.Read, OperationScope.Content) {
			Reply(plugin.callBlocking("run", "{}".encodeToByteArray()).decodeToString())
		}
		GlobalContext.startKoin {
			modules(module { single { OperationRegistry(listOf(reenter), NoProjects) } })
		}

		val error = assertThrows<PluginException> { dispatch("""{"operation":"greet","input":{"name":"Ada"}}""") }
		assertTrue("called again" in error.message!!)
	}

	@Test
	fun `a scope grants its operations and ops list shows only those`() {
		val dispatcher = FakeDispatcher()
		val call = caller(pluginGranted("\"content:read\""), dispatcher)

		assertEquals("Hello", call("greet")["output"]!!.jsonObject["text"]!!.jsonPrimitive.content)
		assertEquals(WasmPlugin.PERMISSION_DENIED, call("wave").errorKind())
		assertEquals(WasmPlugin.PERMISSION_DENIED, call("status").errorKind())
		assertEquals("NotFound", call("nothing").errorKind())

		val listed = call("ops.list")["output"]!!.jsonObject["operations"]!!.jsonArray
		assertEquals(listOf("greet", "ops.list"), listed.map { it.jsonObject["name"]!!.jsonPrimitive.content })
		assertEquals(listOf("greet"), dispatcher.dispatched)
	}

	@Test
	fun `destructive operations must be named`() {
		assertEquals(WasmPlugin.PERMISSION_DENIED, caller(pluginGranted("\"content:write\""), FakeDispatcher())("zap").errorKind())
		assertEquals("Hello", caller(pluginGranted("\"zap\""), FakeDispatcher())("zap")["output"]!!.jsonObject["text"]!!.jsonPrimitive.content)
	}

	@Test
	fun `a failure reaching Hammer comes back to the module`() {
		val reply = caller(pluginGranted("\"greet\""), FakeDispatcher(IllegalStateException("Hammer is busy")))("greet")

		assertEquals(WasmPlugin.FAILED, reply.errorKind())
		assertEquals("Hammer is busy", reply["error"]!!.jsonObject["message"]!!.jsonPrimitive.content)
	}

	private fun pluginGranted(operations: String) = WasmPlugin(
		PluginManifest.parse("id = \"echo\"\nname = \"Echo\"\nversion = \"1\"\napi = 1\n[permissions]\noperations = [$operations]"),
		{ testPlugin("dispatch_echo") },
	)

	private fun caller(plugin: WasmPlugin, dispatcher: Dispatcher): (String) -> JsonObject {
		val route = WasmPlugin.Route(dispatcher)
		return { operation ->
			val request = """{"operation":"$operation","input":{}}""".encodeToByteArray()
			Json.parseToJsonElement(plugin.callBlocking("run", request, route).decodeToString()).jsonObject
		}
	}

	private class FakeDispatcher(private val failure: Exception? = null) : Dispatcher {
		val dispatched = mutableListOf<String>()
		private val operations = listOf(
			descriptor("greet", Access.Read),
			descriptor(OPS_LIST, Access.Read),
			descriptor("wave", Access.Write),
			descriptor("zap", Access.Destructive),
			descriptor("status", Access.Read, OperationScope.Account),
		)

		override suspend fun operations() = operations

		override suspend fun dispatch(operation: String, input: JsonElement): JsonElement {
			dispatched += operation
			failure?.let { throw it }
			return buildJsonObject { put("text", "Hello") }
		}

		private fun descriptor(name: String, access: Access, scope: OperationScope = OperationScope.Content) =
			OperationDescriptor(name, "", access, scope, JsonObject(emptyMap()), JsonObject(emptyMap()))
	}

	private fun JsonObject.errorKind() = this["error"]!!.jsonObject["kind"]!!.jsonPrimitive.content

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
