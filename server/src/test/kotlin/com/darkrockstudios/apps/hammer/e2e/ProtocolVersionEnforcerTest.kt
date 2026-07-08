package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProtocolVersionEnforcerTest : EndToEndTest() {

	@Test
	fun `mismatched protocol version is rejected with 426 and the server version header`(): Unit = runBlocking {
		doStartServer()
		val response: HttpResponse = client().get(api("teapot")) {
			headers {
				append(HAMMER_PROTOCOL_HEADER, (HAMMER_PROTOCOL_VERSION + 1).toString())
			}
		}

		assertEquals(HttpStatusCode.UpgradeRequired, response.status)
		assertEquals(
			HAMMER_PROTOCOL_VERSION.toString(),
			response.headers[HAMMER_PROTOCOL_HEADER],
		)
	}

	@Test
	fun `missing protocol version is rejected with 426`(): Unit = runBlocking {
		doStartServer()
		val response: HttpResponse = client().get(api("teapot"))

		assertEquals(HttpStatusCode.UpgradeRequired, response.status)
	}
}
