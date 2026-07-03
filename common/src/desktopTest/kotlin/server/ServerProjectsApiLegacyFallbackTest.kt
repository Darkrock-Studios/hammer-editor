package server

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue

/**
 * The mutating account endpoints moved from GET to POST. Servers that predate the move only
 * route them as GET, so the client prefers POST and retries once as GET on 404/405.
 */
class ServerProjectsApiLegacyFallbackTest : BaseTest() {

	private val userId = 42L
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@BeforeEach
	override fun setup() {
		super.setup()

		globalSettingsStore = mockk(relaxed = true)
		every { globalSettingsStore.serverSettings } returns ServerSettings(
			ssl = false,
			url = "example.com",
			email = "user@example.com",
			userId = userId,
			bearerToken = "token",
			refreshToken = "refresh",
		)

		setupKoin(
			module {
				single { DeviceLocaleResolver() }
			}
		)
	}

	private fun modernServer() = MockEngine { request ->
		if (request.method == HttpMethod.Post) {
			respond("Okay", HttpStatusCode.OK)
		} else {
			respond("", HttpStatusCode.NotFound)
		}
	}

	private fun legacyServer() = MockEngine { request ->
		if (request.method == HttpMethod.Get) {
			respond("Okay", HttpStatusCode.OK)
		} else {
			respond("", HttpStatusCode.NotFound)
		}
	}

	private fun createApi(engine: MockEngine): ServerProjectsApi {
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
		}
		return ServerProjectsApi(client, globalSettingsStore, TestStrRes())
	}

	@Test
	fun `end sync uses a single POST against a modern server`() = runTest {
		val engine = modernServer()
		val api = createApi(engine)

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isSuccess)
		assertEquals(listOf(HttpMethod.Post), engine.requestHistory.map { it.method })
	}

	@Test
	fun `end sync falls back to GET against a legacy server`() = runTest {
		val engine = legacyServer()
		val api = createApi(engine)

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isSuccess)
		assertEquals("Okay", result.getOrThrow())
		assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), engine.requestHistory.map { it.method })
	}

	@Test
	fun `delete and rename and create fall back to GET against a legacy server`() = runTest {
		val engine = legacyServer()
		val api = createApi(engine)

		assertTrue(api.deleteProject(com.darkrockstudios.apps.hammer.base.ProjectId("uuid-1"), "sync-1").isSuccess)
		assertTrue(api.renameProject(com.darkrockstudios.apps.hammer.base.ProjectId("uuid-1"), "sync-1", "New Name").isSuccess)

		assertEquals(
			listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Post, HttpMethod.Get),
			engine.requestHistory.map { it.method },
		)
	}

	@Test
	fun `a real failure does not trigger the GET fallback`() = runTest {
		val engine = MockEngine { _ ->
			respond("", HttpStatusCode.BadRequest)
		}
		val api = createApi(engine)

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isFailure)
		assertEquals(listOf(HttpMethod.Post), engine.requestHistory.map { it.method })
	}
}
