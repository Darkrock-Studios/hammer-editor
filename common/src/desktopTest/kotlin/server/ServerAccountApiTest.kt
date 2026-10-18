package server

import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.MfaRequiredException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.server.TermsOfServiceRequiredException
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
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
	fun `pairInstall posts the new install id for the signed-in user`() = runTest {
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			ssl = false,
			url = "example.com",
			email = "user@example.com",
			userId = 7L,
			bearerToken = "bearer",
			refreshToken = "refresh",
		)
		var requestPath: String? = null
		var sentInstallId: String? = null
		val engine = MockEngine { request ->
			requestPath = request.url.encodedPath
			sentInstallId = (request.body as FormDataContent).formData["installId"]
			respond(
				content = """{"userId":7,"auth":"watch-auth","refresh":"watch-refresh"}""",
				status = HttpStatusCode.Created,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.pairInstall("watch-install")

		assertEquals("/api/account/pair_install/7", requestPath)
		assertEquals("watch-install", sentInstallId)
		assertEquals("watch-auth", result.getOrThrow().auth)
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

	@Test
	fun `a 401 asking for a second factor fails with the challenge`() = runTest {
		val engine = MockEngine {
			respond(
				content = """{"error":"Second factor required","displayMessage":"Enter the code",""" +
					""""errorCode":"mfa_required","mfa":{"token":"challenge-token","methods":["totp","recovery"]}}""",
				status = HttpStatusCode.Unauthorized,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.login("user@example.com", "password", "install-id")

		val exception = assertIs<MfaRequiredException>(result.exceptionOrNull())
		assertEquals(MfaChallenge(token = "challenge-token", methods = listOf("totp", "recovery")), exception.challenge)
	}

	@Test
	fun `a 401 for a wrong password is a plain http failure`() = runTest {
		val engine = MockEngine {
			respond(
				content = """{"error":"Failed","displayMessage":"Invalid email or password","errorCode":"invalid_credentials"}""",
				status = HttpStatusCode.Unauthorized,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.login("user@example.com", "password", "install-id")

		val exception = assertIs<HttpFailureException>(result.exceptionOrNull())
		assertEquals(ApiErrorCode.INVALID_CREDENTIALS, exception.error.errorCode)
	}

	@Test
	fun `loginMfa posts the challenge token, code and install id`() = runTest {
		var requestPath: String? = null
		var form: FormDataContent? = null
		val engine = MockEngine { request ->
			requestPath = request.url.encodedPath
			form = request.body as FormDataContent
			respond(
				content = """{"userId":7,"auth":"auth-token","refresh":"refresh-token"}""",
				status = HttpStatusCode.OK,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.loginMfa(mfaToken = "challenge-token", code = "123456", installId = "install-id")

		assertEquals("/api/account/login/mfa", requestPath)
		assertEquals("challenge-token", form?.formData?.get("mfaToken"))
		assertEquals("123456", form?.formData?.get("code"))
		assertEquals("install-id", form?.formData?.get("installId"))
		assertEquals("auth-token", result.getOrThrow().auth)
	}

	@Test
	fun `a rejected code is an http failure carrying the server message`() = runTest {
		val engine = MockEngine {
			respond(
				content = """{"error":"Invalid second factor","displayMessage":"That code did not work","errorCode":"mfa_code_invalid"}""",
				status = HttpStatusCode.Unauthorized,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.loginMfa(mfaToken = "challenge-token", code = "000000", installId = "install-id")

		val exception = assertIs<HttpFailureException>(result.exceptionOrNull())
		assertEquals(ApiErrorCode.MFA_CODE_INVALID, exception.error.errorCode)
		assertEquals("That code did not work", exception.error.displayMessage)
	}
}
