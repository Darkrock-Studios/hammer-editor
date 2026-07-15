package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TeapotTest : EndToEndTest() {
	@Test
	fun `Teapot Endpoint`(): Unit = runBlocking {
		doStartServer()
		client().apply {
			val response = get(api("teapot")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
			}

			assertEquals(HttpStatusCode.fromValue(418), response.status)
			assertContains(response.bodyAsText(), "I'm a little Tea Pot")
		}
	}
}