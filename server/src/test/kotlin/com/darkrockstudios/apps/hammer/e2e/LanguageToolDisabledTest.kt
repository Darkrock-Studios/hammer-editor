package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.ServicesResponse
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The server config's default: no LanguageTool routes and no admin page, even with an upstream saved. */
class LanguageToolDisabledTest : EndToEndTest() {

	private val email = "admin@test.com"
	private val password = "password123!@#"

	@Test
	fun `With the feature off, nothing of it is reachable`(): Unit = runBlocking {
		database().serverDatabase.serverConfigQueries.upsertConfig(
			AdminServerConfig.LANGUAGETOOL_CONFIG.key,
			Json.encodeToString(LanguageToolConfig.serializer(), LanguageToolConfig(url = "http://127.0.0.1:1")),
		)
		doStartServer()
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val token = createAuthToken(1, "install-1", database = database(), tokenHasher = tokenHasher()).auth
		val admin = HttpClient { install(HttpCookies) }
		admin.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("email=admin%40test.com&password=password123%21%40%23")
		}

		val services = client().get(api("services/1")) {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header(HttpHeaders.Authorization, "Bearer $token")
		}.body<ServicesResponse>()
		val check = client().submitForm(api("services/1/languagetool/v2/check"), parameters { append("text", "hi") }) {
			header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			header(HttpHeaders.Authorization, "Bearer $token")
		}
		val page = admin.get(route("admin/grammar"))
		val settings = admin.get(route("admin")).bodyAsText()

		assertEquals(ServicesResponse(emptyList()), services)
		assertEquals(HttpStatusCode.NotFound, check.status)
		assertEquals(HttpStatusCode.NotFound, page.status)
		assertFalse(settings.contains("/admin/grammar"))
	}
}
