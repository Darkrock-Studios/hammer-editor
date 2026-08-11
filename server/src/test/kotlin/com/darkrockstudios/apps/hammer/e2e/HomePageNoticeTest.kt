package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomePageNoticeTest : WebEndToEndTest() {

	private val noticeText = "Accounts on this server are by invitation."

	@Test
	fun `home page shows the allowed users notice when a contact email is configured`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount("admin@test.com", "password123!@#")
		database().serverDatabase.serverConfigQueries.upsertConfig("contact_email", "admin@test.com")

		HttpClient().use { web ->
			val response = web.get(route(""))
			assertEquals(HttpStatusCode.OK, response.status)
			assertContains(response.bodyAsText(), noticeText)
		}
	}

	@Test
	fun `home page omits the notice without a contact email`(): Unit = runBlocking {
		doStartServer()
		seedWhitelistedAccount("admin@test.com", "password123!@#")

		HttpClient().use { web ->
			val body = web.get(route("")).bodyAsText()
			assertFalse(body.contains(noticeText))
		}
	}
}
