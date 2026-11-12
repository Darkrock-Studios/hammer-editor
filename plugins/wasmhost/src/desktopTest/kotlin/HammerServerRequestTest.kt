import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.server.HammerServiceClient
import com.darkrockstudios.apps.hammer.common.server.ServiceReply
import com.darkrockstudios.apps.hammer.common.server.ServiceRequest
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.PluginManifest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.WasmPlugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `hammer_server_request`, through `server.wasm`, which sends its input as both the request and the body. */
class HammerServerRequestTest {

	private val sent = mutableListOf<ServiceRequest>()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun plugin(
		services: List<String> = listOf("languagetool"),
		reply: ServiceReply = ServiceReply.Response(200, """{"matches":[]}""".encodeToByteArray()),
	): WasmPlugin = WasmPlugin(
		PluginManifest.parse(
			"""
			id = "server"
			name = "Server"
			version = "1.0.0"
			api = 1

			[permissions]
			hammerServer = [${services.joinToString { "\"$it\"" }}]
			""".trimIndent()
		),
		{ testPlugin("server") },
		hammerServer = HammerServiceClient { request -> sent += request; reply },
	).also { plugin ->
		val registry = PluginRegistry().apply { add(plugin) }
		GlobalContext.startKoin {
			modules(
				module {
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
			)
		}
	}

	private fun WasmPlugin.send(request: String): JsonObject =
		Json.parseToJsonElement(callBlocking("send", request.encodeToByteArray()).decodeToString()).jsonObject

	@Test
	fun `a declared service gets the request, its body, and only the headers a plugin may set`() {
		val request = """{"service":"languagetool","method":"post","path":"/v2/check",""" +
			""""headers":{"Content-Type":"application/x-www-form-urlencoded","Authorization":"Bearer stolen"}}"""

		val reply = plugin().send(request)

		assertEquals(200, reply["status"]!!.jsonPrimitive.int)
		assertEquals("""{"matches":[]}""", reply["body"]!!.jsonPrimitive.content)
		val forwarded = sent.single()
		assertEquals("languagetool", forwarded.service)
		assertEquals("POST", forwarded.method)
		assertEquals("/v2/check", forwarded.path)
		assertEquals(listOf("Content-Type" to "application/x-www-form-urlencoded"), forwarded.headers)
		assertEquals(request, forwarded.body!!.decodeToString())
	}

	@Test
	fun `a service the manifest does not name is refused before it leaves the host`() {
		val reply = plugin(services = listOf("other")).send("""{"service":"languagetool","path":"/v2/check"}""")

		assertEquals("not_permitted", reply["error"]!!.jsonPrimitive.content)
		assertEquals(emptyList(), sent)
	}

	@Test
	fun `paths that could leave the service are refused`() {
		val plugin = plugin()
		val paths = listOf("/../account/refresh_token/1", "v2/check", "/v2/check?x=1", "//evil.example/v2", "/v2/./check", "")

		val errors = paths.map { plugin.send("""{"service":"languagetool","path":"$it"}""")["error"]?.jsonPrimitive?.content }

		assertEquals(List(paths.size) { "invalid" }, errors)
		assertEquals(emptyList(), sent)
	}

	@Test
	fun `methods other than GET and POST are refused`() {
		val reply = plugin().send("""{"service":"languagetool","method":"DELETE","path":"/v2/check"}""")

		assertEquals("invalid", reply["error"]!!.jsonPrimitive.content)
	}

	@Test
	fun `a user who is not signed in is told so`() {
		val notSignedIn = ServiceReply.Failed(ServiceReply.Reason.NotSignedIn, "Not signed in")

		val reply = plugin(reply = notSignedIn).send("""{"service":"languagetool","path":"/v2/check"}""")

		assertEquals("not_signed_in", reply["error"]!!.jsonPrimitive.content)
		assertNull(reply["status"])
	}

	@Test
	fun `a reply that is not text is refused rather than mangled`() {
		val binary = ServiceReply.Response(200, byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))

		val reply = plugin(reply = binary).send("""{"service":"languagetool","path":"/v2/check"}""")

		assertEquals("invalid", reply["error"]!!.jsonPrimitive.content)
	}
}
