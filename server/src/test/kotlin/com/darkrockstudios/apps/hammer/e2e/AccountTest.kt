package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountTest : EndToEndTest() {

	@Test
	fun `Create Account - First User - Whitelist - Success`(): Unit = runBlocking {
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem)
		doStartServer()
		client().apply {
			val response = post(api("account/create")) {
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

			assertEquals(response.status, HttpStatusCode.Created)
		}
	}
}