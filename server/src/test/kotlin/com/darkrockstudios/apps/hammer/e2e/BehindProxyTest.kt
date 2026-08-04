package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.EncryptionConfig
import com.darkrockstudios.apps.hammer.EncryptionMode
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.utils.SERVER_CONFIG_ONE
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The login rate limiter keys on the connecting address. Behind a proxy every request carries
 * the proxy's, so `behindProxy` decides whether one client can exhaust the bucket for everyone.
 */
class BehindProxyTest : EndToEndTest() {

	override val serverConfig = ServerConfig(
		encryption = EncryptionConfig(EncryptionMode.AES),
		behindProxy = true,
	)

	@Test
	fun `a forwarded client address gets its own rate limit bucket`(): Unit = runBlocking {
		createTestServer(SERVER_CONFIG_ONE, fileSystem, database())
		doStartServer()

		// Exhaust the window from one forwarded address.
		repeat(RATE_LIMIT) { attempt ->
			val response = failedLogin(forwardedFor = "203.0.113.10")
			assertEquals(
				HttpStatusCode.Unauthorized,
				response.status,
				"Attempt ${attempt + 1} should still be inside the window",
			)
		}
		assertEquals(
			HttpStatusCode.TooManyRequests,
			failedLogin(forwardedFor = "203.0.113.10").status,
			"The exhausted address must be limited",
		)

		assertEquals(
			HttpStatusCode.Unauthorized,
			failedLogin(forwardedFor = "203.0.113.99").status,
			"A different forwarded address must not inherit the first one's exhausted bucket",
		)
	}

	private suspend fun failedLogin(forwardedFor: String) = client().post(api("account/login")) {
		headers {
			append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
			append(HttpHeaders.XForwardedFor, forwardedFor)
		}
		setBody(
			FormDataContent(
				Parameters.build {
					append("email", "test@example.com")
					append("password", "definitely-not-the-password")
					append("installId", "fake-install-id")
				}
			)
		)
	}

	private companion object {
		const val RATE_LIMIT = 10
	}
}
