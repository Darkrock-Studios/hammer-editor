package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains

class TeapotTest : EndToEndTest() {
	@Test
	fun `Teapot Endpoint`(): Unit = runBlocking {
		doStartServer()
		client().apply {
			val response: String = get(api("teapot")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
			}.body()

			assertContains(response, "I'm a little Tea Pot")
		}
	}
}