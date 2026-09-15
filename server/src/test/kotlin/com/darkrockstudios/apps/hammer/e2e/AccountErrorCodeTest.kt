package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Every failure the account routes can answer with, mapped to the status and
 * [ApiErrorCode] a client branches on. A wrong status here is what made a rejected
 * signup indistinguishable from a real conflict (#835).
 */
class AccountErrorCodeTest : EndToEndTest() {

	@Test
	fun `Create Account - an existing email is a 409 naming the conflict`(): Unit = runBlocking {
		startEmptyServer()

		val first = createAccount("taken@example.com", VALID_PASSWORD)
		assertEquals(HttpStatusCode.Created, first.status)

		val second = createAccount("taken@example.com", VALID_PASSWORD)

		assertEquals(HttpStatusCode.Conflict, second.status)
		assertEquals(ApiErrorCode.ACCOUNT_EXISTS, second.errorCode())
	}

	@Test
	fun `Create Account - an email pending deletion is a 409, not a plain conflict`(): Unit = runBlocking {
		startEmptyServer()

		assertEquals(HttpStatusCode.Created, createAccount("leaving@example.com", VALID_PASSWORD).status)
		// Soft delete keeps the row, which reserves the email for the retention window.
		database().executeAsync("UPDATE account SET deleted_at = NOW() WHERE email = 'leaving@example.com'")

		val response = createAccount("leaving@example.com", VALID_PASSWORD)

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.ACCOUNT_PENDING_DELETION, response.errorCode())
	}

	@Test
	fun `Create Account - a malformed email is a 400`(): Unit = runBlocking {
		startEmptyServer()

		val response = createAccount("not-an-email", VALID_PASSWORD)

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertEquals(ApiErrorCode.INVALID_EMAIL, response.errorCode())
	}

	@Test
	fun `Create Account - a short password is a 400 naming the rule`(): Unit = runBlocking {
		startEmptyServer()

		val response = createAccount("short@example.com", "x".repeat(PasswordValidator.MIN_LENGTH - 1))

		assertEquals(HttpStatusCode.BadRequest, response.status)
		assertEquals(ApiErrorCode.PASSWORD_TOO_SHORT, response.errorCode())
	}

	@Test
	fun `Refresh Token - an unknown token is a 401 the client can act on`(): Unit = runBlocking {
		startEmptyServer()

		assertEquals(HttpStatusCode.Created, createAccount("refresh@example.com", VALID_PASSWORD).status)

		val response = client().post(api("account/refresh_token/1")) {
			headers { append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString()) }
			setBody(
				FormDataContent(
					Parameters.build {
						append("installId", INSTALL_ID)
						append("refreshToken", "not-a-real-refresh-token")
					}
				)
			)
		}

		assertEquals(HttpStatusCode.Unauthorized, response.status)
		assertEquals(ApiErrorCode.TOKEN_INVALID, response.errorCode())
	}

	private suspend fun startEmptyServer() {
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database())
		doStartServer()
	}

	private suspend fun createAccount(email: String, password: String): HttpResponse =
		client().post(api("account/create")) {
			headers { append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString()) }
			setBody(
				FormDataContent(
					Parameters.build {
						append("email", email)
						append("password", password)
						append("installId", INSTALL_ID)
					}
				)
			)
		}

	private suspend fun HttpResponse.errorCode(): String? =
		Json.decodeFromString<HttpResponseError>(bodyAsText()).errorCode

	companion object {
		private const val VALID_PASSWORD = "password123!@#"
		private const val INSTALL_ID = "fake-install-id"
	}
}
