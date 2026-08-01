package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.frontend.utils.SWAP_ERROR_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * The dashboard's rejection paths answer with an error status and an out-of-band toast. htmx
 * discards the body of a 4xx unless the response says otherwise, so without these headers the
 * user gets no feedback at all.
 */
class DashboardErrorToastTest : EndToEndTest() {

	private val email = "author@test.com"
	private val password = "password123!@#"

	private fun seed() = runBlocking {
		E2eTestData.createAccount(TestAccount(email, password), database())
		database().serverDatabase.whiteListQueries
			.addToWhiteList(email, Clock.System.now(), "Test author", null)
	}

	private suspend fun login(): HttpClient {
		val authed = HttpClient { install(HttpCookies) }
		val response = authed.post(route("login")) {
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(
				"email=${URLEncoder.encode(email, "UTF-8")}" +
					"&password=${URLEncoder.encode(password, "UTF-8")}"
			)
		}
		assertEquals(HttpStatusCode.Found, response.status)
		return authed
	}

	private suspend fun HttpClient.postForm(path: String, body: String): HttpResponse =
		post(route(path)) {
			header("HX-Request", "true")
			contentType(ContentType.Application.FormUrlEncoded)
			setBody(body)
		}

	@Test
	fun `a rejected pen name answers with a toast htmx will swap`(): Unit = runBlocking {
		doStartServer()
		seed()
		val authed = login()

		val response = authed.postForm("dashboard/penname", "penName=")

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		// The form targets the displayed pen name; an empty error body must not wipe it.
		assertEquals("none", response.headers["HX-Reswap"])
		assertContains(response.bodyAsText(), "toast-error")
	}

	@Test
	fun `a mismatched deletion confirmation answers with a toast htmx will swap`(): Unit = runBlocking {
		doStartServer()
		seed()
		val authed = login()

		val response = authed.postForm("dashboard/delete-account", "confirmEmail=someone@else.com")

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertEquals("true", response.headers[SWAP_ERROR_HEADER])
		assertContains(response.bodyAsText(), "toast-error")
	}
}
