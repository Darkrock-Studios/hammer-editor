import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginException
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

	private val greet = operation<Greeting, Reply>("greet", "", Access.Read) {
		if (it.name.isBlank()) notFound("Nobody to greet")
		Reply("Hello, ${it.name}")
	}
	private val wave = operation<Greeting, Reply>("wave", "", Access.Read) { Reply("~") }

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
		val reenter = operation<Greeting, Reply>("greet", "", Access.Read) {
			Reply(plugin.callBlocking("run", "{}".encodeToByteArray()).decodeToString())
		}
		GlobalContext.startKoin {
			modules(module { single { OperationRegistry(listOf(reenter), NoProjects) } })
		}

		val error = assertThrows<PluginException> { dispatch("""{"operation":"greet","input":{"name":"Ada"}}""") }
		assertTrue("called again" in error.message!!)
	}

	private fun JsonObject.errorKind() = this["error"]!!.jsonObject["kind"]!!.jsonPrimitive.content

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
