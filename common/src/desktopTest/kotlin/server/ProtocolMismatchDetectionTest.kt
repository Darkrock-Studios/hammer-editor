package server

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.protocolmismatch.ProtocolMismatchRepository
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import org.koin.test.get
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The server answers a protocol mismatch with 426 Upgrade Required (API errors carry no body,
 * so the status is the only signal). The client turns that into a mismatch notification.
 */
class ProtocolMismatchDetectionTest : BaseTest() {

	private lateinit var globalSettingsStore: GlobalSettingsStore

	@BeforeEach
	override fun setup() {
		super.setup()

		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			ssl = false,
			url = "example.com",
			email = "user@example.com",
			userId = 42L,
			bearerToken = "token",
			refreshToken = "refresh",
		)

		setupKoin(
			module {
				single { DeviceLocaleResolver() }
			}
		)
	}

	private fun createApi(engine: MockEngine): ServerProjectsApi {
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		}
		return ServerProjectsApi(client, globalSettingsStore, TestStrRes())
	}

	@Test
	fun `a 426 response notifies the repository with the server protocol version`() = runTest {
		val serverVersion = HAMMER_PROTOCOL_VERSION + 2
		val engine = MockEngine {
			respond(
				content = "",
				status = HttpStatusCode.UpgradeRequired,
				headers = headersOf(HAMMER_PROTOCOL_HEADER, serverVersion.toString()),
			)
		}
		val api = createApi(engine)
		val repository = get<ProtocolMismatchRepository>()

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isFailure)
		val info = repository.mismatches.replayCache.firstOrNull()
		assertNotNull(info)
		assertEquals(HAMMER_PROTOCOL_VERSION, info.clientProtocolVersion)
		assertEquals(serverVersion, info.serverProtocolVersion)
		assertTrue(info.clientIsBehind)
	}

	@Test
	fun `a 426 without a version header still notifies and defaults to client behind`() = runTest {
		val engine = MockEngine { respond("", HttpStatusCode.UpgradeRequired) }
		val api = createApi(engine)
		val repository = get<ProtocolMismatchRepository>()

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isFailure)
		val info = repository.mismatches.replayCache.firstOrNull()
		assertNotNull(info)
		assertNull(info.serverProtocolVersion)
		assertTrue(info.clientIsBehind)
	}

	@Test
	fun `an ordinary failure does not notify the repository`() = runTest {
		val engine = MockEngine { respond("", HttpStatusCode.BadRequest) }
		val api = createApi(engine)
		val repository = get<ProtocolMismatchRepository>()

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isFailure)
		assertTrue(repository.mismatches.replayCache.isEmpty())
	}
}
