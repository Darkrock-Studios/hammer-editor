package server

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.server_error_connection_generic
import com.darkrockstudios.apps.hammer.server_error_tls
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import utils.BaseTest
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A server reachable only over plain HTTP fails the client's TLS handshake, which needs to read as
 * "this server isn't serving HTTPS" rather than as a generic connectivity problem.
 */
class ApiTlsFailureTest : BaseTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var strRes: RecordingStrRes

	@BeforeEach
	override fun setup() {
		super.setup()

		strRes = RecordingStrRes()
		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			url = "homeserver:8080",
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

	private fun createApi(failure: Throwable): ServerAccountApi {
		val client = HttpClient(MockEngine { throw failure }) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		}
		return ServerAccountApi(client, globalSettingsStore, strRes)
	}

	@Test
	fun `a handshake failure reports the server is not serving https`() = runTest {
		val api = createApi(SSLHandshakeException("Remote host terminated the handshake"))

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_tls))
	}

	@Test
	fun `a wrapped handshake failure is still recognised`() = runTest {
		val api = createApi(
			java.io.IOException("request failed", SSLHandshakeException("unable to find valid certification path"))
		)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_tls))
	}

	@Test
	fun `an ordinary connection failure keeps the generic message`() = runTest {
		val api = createApi(ConnectException("Connection refused"))

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_connection_generic))
	}
}
