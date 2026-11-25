package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertEquals

class AdminServerSettingsTest : EndToEndTest() {

	private val email = "admin@test.com"
	private val password = "password123!@#"

	private fun savedValue(key: String): String? =
		database().serverDatabase.serverConfigQueries.getConfig(key).executeAsOneOrNull()

	private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

	@Test
	fun `saving settings stores the trimmed server name and tagline, capped in length`(): Unit = runBlocking {
		doStartServer()
		E2eTestData.createAccount(TestAccount(email, password, isAdmin = true), database())
		val admin = HttpClient { install(HttpCookies) }
		admin.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("email=${encode(email)}&password=${encode(password)}")
		}

		val response = admin.post(route("admin/settings")) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody("name=${encode("  Thursday Night Writers  ")}&tagline=${encode("x".repeat(400))}")
		}

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("Thursday Night Writers", savedValue(AdminServerConfig.SERVER_NAME.key))
		assertEquals(280, savedValue(AdminServerConfig.SERVER_TAGLINE.key)?.length)
	}
}
