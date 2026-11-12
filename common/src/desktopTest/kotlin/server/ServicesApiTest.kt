package server

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.server.ServiceReply
import com.darkrockstudios.apps.hammer.common.server.ServiceRequest
import com.darkrockstudios.apps.hammer.common.server.ServicesApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServicesApiTest {

	private val signedIn = ServerSettings(
		ssl = true,
		url = "hammer.example.com",
		email = "writer@example.com",
		userId = 42,
		bearerToken = "token",
		refreshToken = "refresh",
	)
	private val requests = mutableListOf<HttpRequestData>()

	private fun api(
		settings: ServerSettings? = signedIn,
		handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
			respond("""{"matches":[]}""", HttpStatusCode.OK)
		},
	): ServicesApi {
		val store = mockk<GlobalSettingsStore> { every { serverSettings } returns settings }
		val client = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
			install(HttpTimeout)
		}
		return ServicesApi(client, store, ProtocolMismatchRepository())
	}

	private val check = ServiceRequest(
		service = "languagetool",
		method = "POST",
		path = "/v2/check",
		headers = listOf("Content-Type" to "application/x-www-form-urlencoded"),
		body = "text=Hi".encodeToByteArray(),
	)

	@Test
	fun `A request goes to the service under the signed-in user`() = runTest {
		val reply = api().request(check)

		assertIs<ServiceReply.Response>(reply)
		assertEquals(200, reply.status)
		assertEquals("""{"matches":[]}""", reply.body.decodeToString())
		val sent = requests.single()
		assertEquals("https://hammer.example.com/api/services/42/languagetool/v2/check", sent.url.toString())
		assertEquals("text=Hi", sent.body.toByteArray().decodeToString())
		assertEquals(ContentType.Application.FormUrlEncoded, sent.body.contentType)
	}

	@Test
	fun `A session the refresh couldn't save is not signed in`() = runTest {
		val reply = api { respond("", HttpStatusCode.Unauthorized) }.request(check)

		assertEquals(ServiceReply.Reason.NotSignedIn, (reply as ServiceReply.Failed).reason)
	}

	@Test
	fun `Without a sync account nothing is sent`() = runTest {
		val reply = api(settings = null).request(check)

		assertEquals(ServiceReply.Reason.NotSignedIn, (reply as ServiceReply.Failed).reason)
		assertEquals(emptyList(), requests)
	}

	@Test
	fun `A server without the service is unavailable, other statuses pass through`() = runTest {
		val missing = api { respond("", HttpStatusCode.NotFound) }.request(check)
		val limited = api { respond("slow down", HttpStatusCode.TooManyRequests) }.request(check)

		assertEquals(ServiceReply.Reason.Unavailable, (missing as ServiceReply.Failed).reason)
		assertEquals(429, (limited as ServiceReply.Response).status)
	}

	@Test
	fun `An unreachable server is a network failure`() = runTest {
		val reply = api { throw IOException("Connection refused") }.request(check)

		assertEquals(ServiceReply.Reason.Network, (reply as ServiceReply.Failed).reason)
	}
}
