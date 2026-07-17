package server

import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.TermsOfServiceRequiredException
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
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
import utils.TestStrRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerAccountApiTest : BaseTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore

	@BeforeEach
	override fun setup() {
		super.setup()

		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			ssl = false,
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

	private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

	private fun createApi(engine: MockEngine): ServerAccountApi {
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		}
		return ServerAccountApi(client, globalSettingsStore, TestStrRes())
	}

	@Test
	fun `a successful create returns the token`() = runTest {
		val engine = MockEngine {
			respond(
				content = """{"userId":1,"auth":"auth-token","refresh":"refresh-token"}""",
				status = HttpStatusCode.Created,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertTrue(result.isSuccess)
		assertEquals("auth-token", result.getOrThrow().auth)
	}

	@Test
	fun `a 451 with a challenge body fails with a terms-of-service exception`() = runTest {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")
		val engine = MockEngine {
			respond(
				content = """{"text":"${challenge.text}","version":"${challenge.version}"}""",
				status = HttpStatusCode.fromValue(451),
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertTrue(result.isFailure)
		val exception = assertIs<TermsOfServiceRequiredException>(result.exceptionOrNull())
		assertEquals(challenge, exception.challenge)
	}

	@Test
	fun `a 451 with a malformed body falls through to the default failure`() = runTest {
		val engine = MockEngine {
			respond(
				content = "not a challenge",
				status = HttpStatusCode.fromValue(451),
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertTrue(result.isFailure)
		assertIs<HttpFailureException>(result.exceptionOrNull())
	}
}
