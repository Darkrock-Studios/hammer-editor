package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.utils.SERVER_CONFIG_ONE
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountTest : EndToEndTest() {

	@Test
	fun `Create Account the First User with Whitelist enabled, who becomes Admin`(): Unit =
		runBlocking {
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database())
		doStartServer()
		client().apply {
			val response = post(api("account/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", "test@example.com")
							append("password", "password123!@#")
							append("installId", "fake-install-id")
						}
					)
				)
			}

			assertEquals(HttpStatusCode.Created, response.status)
			val body = response.bodyAsText()
			val token = Json.decodeFromString<Token>(body)
			assertEquals(1, token.userId)
			assertTrue(token.auth.isNotBlank())
			assertEquals(64, token.auth.length)
		}
	}

	@Test
	fun `Create Account - Second User - Not on Whitelist - Failure`(): Unit = runBlocking {
		createTestServer(SERVER_CONFIG_ONE, fileSystem, database())
		doStartServer()
		client().apply {
			val response = post(api("account/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", "test2@example.com")
							append("password", "password123!@#")
							append("installId", "fake-install-id")
						}
					)
				)
			}

			assertEquals(HttpStatusCode.Conflict, response.status)
		}
	}

	@Test
	fun `Create Account - Second User - Is on Whitelist - Success`(): Unit = runBlocking {
		createTestServer(SERVER_CONFIG_ONE, fileSystem, database())
		doStartServer()
		client().apply {
			val response = post(api("account/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", "test3@example.com")
							append("password", "password123!@#")
							append("installId", "fake-install-id")
						}
					)
				)
			}

			assertEquals(HttpStatusCode.Created, response.status)
		}
	}

	@Test
	fun `Login - First User - Success`(): Unit = runBlocking {
		createTestServer(SERVER_CONFIG_ONE, fileSystem, database())
		doStartServer()
		client().apply {
			val response = post(api("account/login")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", "test@example.com")
							append("password", "password123!@#")
							append("installId", "fake-install-id")
						}
					)
				)
			}

			assertEquals(HttpStatusCode.OK, response.status)
			val body = response.bodyAsText()
			val token = Json.decodeFromString<Token>(body)
			assertEquals(1, token.userId)
			assertTrue(token.auth.isNotBlank())
			assertEquals(64, token.auth.length)
		}
	}

	@Test
	fun `Login - First User - Bad Password`(): Unit = runBlocking {
		createTestServer(SERVER_CONFIG_ONE, fileSystem, database())
		doStartServer()
		client().apply {
			val response = post(api("account/login")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
				setBody(
					FormDataContent(
						Parameters.build {
							append("email", "test@example.com")
							append("password", "password123#")
							append("installId", "fake-install-id")
						}
					)
				)
			}

			assertEquals(HttpStatusCode.Unauthorized, response.status)
		}
	}
}