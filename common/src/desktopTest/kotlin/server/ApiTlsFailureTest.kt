package server

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerAccountApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.server_error_connection_generic
import com.darkrockstudios.apps.hammer.server_error_network_unavailable
import com.darkrockstudios.apps.hammer.server_error_tls
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import utils.BaseTest
import java.io.UncheckedIOException
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A server reachable only over plain HTTP fails the client's TLS handshake, which needs to read as
 * "this server isn't serving HTTPS" rather than as a generic connectivity problem. Covers the rest
 * of the network failure mapping too, including the unchecked wrappers the JDK throws.
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

	/**
	 * `java.net.http.HttpClient`'s constructor wraps a failed `Selector.open()` this way, which is
	 * how a Windows machine that cannot open the JDK's loopback socket pair fails. Nothing reached
	 * the network, so it must not read as a server or connectivity problem.
	 */
	@Test
	fun `an unchecked io wrapper reports the network being unavailable`() = runTest {
		val api = createApi(
			UncheckedIOException(java.io.IOException("Unable to establish loopback connection"))
		)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_network_unavailable))
		assertFalse(strRes.requested.contains(Res.string.server_error_connection_generic))
	}

	@Test
	fun `an unchecked io wrapper still recognises a tls failure`() = runTest {
		val api = createApi(
			UncheckedIOException(
				java.io.IOException("request failed", SSLHandshakeException("bad certificate"))
			)
		)

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_tls))
	}

	@Test
	fun `an unchecked non-io failure propagates`() = runTest {
		val api = createApi(IllegalStateException("boom"))

		assertFailsWith<IllegalStateException> {
			api.createAccount("user@example.com", "password", "install-id")
		}
	}

	@Test
	fun `an ordinary connection failure keeps the generic message`() = runTest {
		val api = createApi(ConnectException("Connection refused"))

		val result = api.createAccount("user@example.com", "password", "install-id")

		assertIs<HttpFailureException>(result.exceptionOrNull())
		assertTrue(strRes.requested.contains(Res.string.server_error_connection_generic))
		assertFalse(strRes.requested.contains(Res.string.server_error_network_unavailable))
	}
}

private class RecordingStrRes : StrRes {
	val requested = mutableListOf<StringResource>()

	override suspend fun get(str: StringResource): String {
		requested += str
		return "test"
	}

	override suspend fun get(str: StringResource, vararg args: Any): String {
		requested += str
		return "test"
	}
}
