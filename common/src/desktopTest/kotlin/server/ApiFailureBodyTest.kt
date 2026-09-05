package server

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.sync_general_error
import com.darkrockstudios.apps.hammer.sync_unauthorized
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the server said about a failure has to survive the trip to the user. A 401
 * explaining that the password was wrong used to be replaced by the generic sync
 * message before anyone could read it (#835).
 */
class ApiFailureBodyTest : BaseTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var strRes: RecordingStrRes

	@BeforeEach
	override fun setup() {
		super.setup()

		strRes = RecordingStrRes()
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			url = "example.com",
			email = "user@example.com",
			userId = -1L,
			bearerToken = null,
			refreshToken = null,
		)

		setupKoin(
			module {
				single { DeviceLocaleResolver() }
			}
		)
	}

	@Test
	fun `a 401 carrying a message reaches the caller as that message`() = runTest {
		val api = createApi(
			status = HttpStatusCode.Unauthorized,
			body = """{"error":"Failed to authenticate","displayMessage":"Invalid email or password",""" +
				""""errorCode":"${ApiErrorCode.INVALID_CREDENTIALS}"}""",
			contentType = "application/json",
		)

		val failure = api.login("user@example.com", "password", "install-id").exceptionOrNull()

		val httpFailure = assertIs<HttpFailureException>(failure)
		assertEquals("Invalid email or password", httpFailure.error.displayMessage)
		assertEquals(ApiErrorCode.INVALID_CREDENTIALS, httpFailure.error.errorCode)
		assertFalse(
			strRes.requested.contains(Res.string.sync_unauthorized),
			"A message from the server must not be replaced by the generic one",
		)
	}

	@Test
	fun `a 403 naming the allowed-users list keeps its own message`() = runTest {
		val api = createApi(
			status = HttpStatusCode.Forbidden,
			body = """{"error":"Failed to authenticate","displayMessage":"Not allowed on this server",""" +
				""""errorCode":"${ApiErrorCode.NOT_WHITELISTED}"}""",
			contentType = "application/json",
		)

		val failure = api.login("user@example.com", "password", "install-id").exceptionOrNull()

		val httpFailure = assertIs<HttpFailureException>(failure)
		assertEquals(HttpStatusCode.Forbidden, httpFailure.statusCode)
		assertEquals(ApiErrorCode.NOT_WHITELISTED, httpFailure.error.errorCode)
	}

	@Test
	fun `a 401 with nothing usable falls back to the unauthorized message`() = runTest {
		val api = createApi(status = HttpStatusCode.Unauthorized, body = "")

		val failure = api.login("user@example.com", "password", "install-id").exceptionOrNull()

		assertIs<HttpFailureException>(failure)
		assertTrue(strRes.requested.contains(Res.string.sync_unauthorized))
	}

	@Test
	fun `another status with nothing usable falls back to the general message`() = runTest {
		val api = createApi(status = HttpStatusCode.InternalServerError, body = "")

		val failure = api.login("user@example.com", "password", "install-id").exceptionOrNull()

		assertIs<HttpFailureException>(failure)
		assertTrue(strRes.requested.contains(Res.string.sync_general_error))
	}

	private fun createApi(
		status: HttpStatusCode,
		body: String,
		contentType: String? = null,
	): ServerAccountApi {
		val engine = MockEngine {
			respond(
				content = body,
				status = status,
				headers = contentType?.let { headersOf(HttpHeaders.ContentType, it) } ?: headersOf(),
			)
		}
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		}
		return ServerAccountApi(client, globalSettingsStore, strRes)
	}
}
