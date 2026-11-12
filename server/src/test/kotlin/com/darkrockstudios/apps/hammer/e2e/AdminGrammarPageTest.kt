package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.ServicesResponse
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AdminGrammarPageTest : EndToEndTest() {

	override val serverConfig = super.serverConfig.copy(languageToolEnabled = true)

	private val email = "admin@test.com"
	private val password = "password123!@#"
	private var upstream: EmbeddedServer<*, *>? = null

	@AfterEach
	fun stopUpstream() {
		upstream?.stop(100, 500)
	}

	private fun startUpstream(): Int {
		val server = embeddedServer(Jetty, port = 0, host = "127.0.0.1") {
			routing {
				get("/v2/languages") {
					call.respondText(
						"""[{"name":"English","code":"en","longCode":"en-US"},{"name":"German","code":"de","longCode":"de-DE"}]""",
						ContentType.Application.Json,
					)
				}
			}
		}
		server.start()
		upstream = server
		return runBlocking { server.engine.resolvedConnectors().first().port }
	}

	private suspend fun login(): HttpClient {
		val authed = HttpClient { install(HttpCookies) }
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("email=${URLEncoder.encode(email, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}")
		}
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	private suspend fun HttpClient.htmxPost(path: String, body: String) = post(route(path)) {
		header("HX-Request", "true")
		contentType(ContentType.Application.FormUrlEncoded)
		setBody(body)
	}

	private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

	@Test
	fun `Saving the settings turns the service on for writers`(): Unit = runBlocking {
		val port = startUpstream()
		doStartServer()
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val token = createAuthToken(1, "install-1", database = database(), tokenHasher = tokenHasher()).auth
		val admin = login()

		val before = admin.get(route("admin/grammar")).bodyAsText()
		val saved = admin.htmxPost(
			"admin/grammar/settings",
			"url=${encode("http://127.0.0.1:$port")}&requestsPerMinute=30&maxRequestBytes=50000",
		)
		val after = admin.get(route("admin/grammar")).bodyAsText()
		val services = client().get(api("services/1")) {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header(HttpHeaders.Authorization, "Bearer $token")
		}.body<ServicesResponse>()

		assertContains(before, "status-badge--inactive")
		assertEquals(HttpStatusCode.OK, saved.status)
		assertContains(after, "status-badge--active")
		assertContains(after, "value=\"30\"")
		assertEquals(ServicesResponse(listOf("languagetool")), services)
	}

	@Test
	fun `An address without a scheme is not saved`(): Unit = runBlocking {
		doStartServer()
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val admin = login()

		val saved = admin.htmxPost("admin/grammar/settings", "url=${encode("languagetool:8010")}")
		val page = admin.get(route("admin/grammar")).bodyAsText()

		assertEquals(HttpStatusCode.BadRequest, saved.status)
		assertContains(page, "status-badge--inactive")
	}

	@Test
	fun `Test connection counts the upstream's languages`(): Unit = runBlocking {
		val port = startUpstream()
		doStartServer()
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val admin = login()

		val reached = admin.htmxPost("admin/grammar/test", "url=${encode("http://127.0.0.1:$port/v2")}").bodyAsText()
		val unreachable = admin.htmxPost("admin/grammar/test", "url=${encode("http://127.0.0.1:1")}").bodyAsText()

		assertContains(reached, "Connected: 2 languages available")
		assertContains(unreachable, "Could not reach LanguageTool")
	}
}
