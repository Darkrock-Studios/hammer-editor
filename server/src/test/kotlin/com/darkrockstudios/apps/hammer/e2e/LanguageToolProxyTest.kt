package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.ServicesResponse
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAccount
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageToolProxyTest : EndToEndTest() {

	override val serverConfig = super.serverConfig.copy(languageToolEnabled = true)

	private val userId = 1L
	private val upstreamReply = """{"matches":[]}"""
	private var upstreamStatus = HttpStatusCode.OK
	private val receivedForms = java.util.concurrent.CopyOnWriteArrayList<Parameters>()
	private var upstream: EmbeddedServer<*, *>? = null
	private var upstreamPort = 0

	@AfterEach
	fun stopUpstream() {
		upstream?.stop(100, 500)
	}

	private fun startUpstream() {
		val server = embeddedServer(Jetty, port = 0, host = "127.0.0.1") {
			routing {
				post("/v2/check") {
					receivedForms.add(call.receiveParameters())
					call.respondText(upstreamReply, ContentType.Application.Json, upstreamStatus)
				}
			}
		}
		server.start()
		upstream = server
		upstreamPort = runBlocking { server.engine.resolvedConnectors().first().port }
	}

	private fun configure(config: LanguageToolConfig) {
		database().serverDatabase.serverConfigQueries.upsertConfig(
			AdminServerConfig.LANGUAGETOOL_CONFIG.key,
			Json.encodeToString(LanguageToolConfig.serializer(), config),
		)
	}

	private fun upstreamConfig(requestsPerMinute: Int = 60, maxRequestBytes: Int = 100_000) = LanguageToolConfig(
		url = "http://127.0.0.1:$upstreamPort/v2/check",
		requestsPerMinute = requestsPerMinute,
		maxRequestBytes = maxRequestBytes,
	)

	private fun account(email: String, id: Long): String {
		createAccount(TestAccount(email, "password123!@#"), database())
		return createAuthToken(id, "install-$id", database = database(), tokenHasher = tokenHasher()).auth
	}

	private fun HttpRequestBuilder.auth(token: String) {
		headers {
			append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			append(HttpHeaders.Authorization, "Bearer $token")
		}
	}

	private suspend fun check(token: String, id: Long = userId, form: Parameters = parameters {
		append("text", "This are a test.")
		append("language", "en-US")
	}): HttpResponse = client().submitForm(api("services/$id/languagetool/v2/check"), form) { auth(token) }

	@Test
	fun `An unconfigured server offers no service`(): Unit = runBlocking {
		val token = account("a@test.com", userId)
		doStartServer()

		val services = client().get(api("services/$userId")) { auth(token) }
		assertEquals(ServicesResponse(emptyList()), services.body<ServicesResponse>())
		assertEquals(HttpStatusCode.NotFound, check(token).status)
	}

	@Test
	fun `A check is forwarded without credentials and the reply passed back`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig())
		val token = account("a@test.com", userId)
		doStartServer()

		val services = client().get(api("services/$userId")) { auth(token) }
		assertEquals(ServicesResponse(listOf("languagetool")), services.body<ServicesResponse>())

		val response = check(token, form = parameters {
			append("text", "This are a test.")
			append("language", "en-US")
			append("level", "picky")
			append("username", "someone@example.com")
			append("apiKey", "secret")
		})

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals(upstreamReply, response.bodyAsText())
		val forwarded = receivedForms.single()
		assertEquals(setOf("text", "language", "level"), forwarded.names())
		assertEquals("This are a test.", forwarded["text"])
	}

	@Test
	fun `A check needs a signed-in user`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig())
		account("a@test.com", userId)
		doStartServer()

		assertEquals(HttpStatusCode.Unauthorized, check("not-a-token").status)
		assertTrue(receivedForms.isEmpty())
	}

	@Test
	fun `The upstream refusing becomes a bad gateway, never an auth failure`(): Unit = runBlocking {
		startUpstream()
		upstreamStatus = HttpStatusCode.Unauthorized
		configure(upstreamConfig())
		val token = account("a@test.com", userId)
		doStartServer()

		assertEquals(HttpStatusCode.BadGateway, check(token).status)
	}

	@Test
	fun `The upstream's own bad request is passed through`(): Unit = runBlocking {
		startUpstream()
		upstreamStatus = HttpStatusCode.BadRequest
		configure(upstreamConfig())
		val token = account("a@test.com", userId)
		doStartServer()

		assertEquals(HttpStatusCode.BadRequest, check(token).status)
	}

	@Test
	fun `An unreachable upstream is a bad gateway`(): Unit = runBlocking {
		configure(LanguageToolConfig(url = "http://127.0.0.1:1"))
		val token = account("a@test.com", userId)
		doStartServer()

		assertEquals(HttpStatusCode.BadGateway, check(token).status)
	}

	@Test
	fun `A body over the cap is refused before it reaches the upstream`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig(maxRequestBytes = 1_000))
		val token = account("a@test.com", userId)
		doStartServer()

		val response = check(token, form = parameters { append("text", "x".repeat(2_000)) })

		assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
		assertTrue(receivedForms.isEmpty())
	}

	@Test
	fun `A body that is not a form is refused`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig())
		val token = account("a@test.com", userId)
		doStartServer()

		val response = client().post(api("services/$userId/languagetool/v2/check")) {
			auth(token)
			contentType(ContentType.Application.Json)
			setBody("""{"text":"hi"}""")
		}

		assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
	}

	@Test
	fun `Each user has their own rate limit`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig(requestsPerMinute = 2))
		val first = account("a@test.com", 1)
		val second = account("b@test.com", 2)
		doStartServer()

		assertEquals(HttpStatusCode.OK, check(first, 1).status)
		assertEquals(HttpStatusCode.OK, check(first, 1).status)
		val limited = check(first, 1)
		assertEquals(HttpStatusCode.TooManyRequests, limited.status)
		assertTrue(limited.headers[HttpHeaders.RetryAfter]!!.toInt() in 1..60)
		assertEquals(HttpStatusCode.OK, check(second, 2).status)
	}

	@Test
	fun `A refused request does not spend the caller's limit`(): Unit = runBlocking {
		startUpstream()
		configure(upstreamConfig(requestsPerMinute = 1, maxRequestBytes = 1_000))
		val token = account("a@test.com", userId)
		doStartServer()

		val tooLarge = check(token, form = parameters { append("text", "x".repeat(2_000)) })

		assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
		assertEquals(HttpStatusCode.OK, check(token).status)
	}
}
