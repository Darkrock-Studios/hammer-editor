package server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
import kotlin.test.assertFalse

/**
 * The project is identified in the URL path by its [ProjectId]. That id is normally a UUID,
 * but it must still be encoded so a reserved character could never split the path into extra
 * segments or escape the intended `{userId}/{projectId}/{action}` template.
 */
class ServerApiPathEncodingTest : BaseTest() {

	private val userId = 42L
	private lateinit var json: Json
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private var capturedUrl: Url? = null

	@BeforeEach
	override fun setup() {
		super.setup()
		json = Json { ignoreUnknownKeys = true }

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

	private fun mockClient(): HttpClient {
		val engine = MockEngine { request ->
			capturedUrl = request.url
			respond(
				content = "",
				status = HttpStatusCode.NoContent,
				headers = headersOf("Content-Type", "application/json"),
			)
		}
		return HttpClient(engine) {
			install(ContentNegotiation) { json(json) }
		}
	}

	private suspend fun captureUrlOf(call: suspend () -> Unit): Url {
		runCatching { call() }
		return requireNotNull(capturedUrl) { "No request was captured" }
	}

	@Test
	fun `malicious project id is a single encoded path segment for ProjectDataApi`() = runTest {
		val client = mockClient()
		val api = ProjectDataApi(client, globalSettingsStore, json, TestStrRes())

		val maliciousId = "p/../../../api/account/test_auth"

		val url = captureUrlOf {
			api.getProjectData(userId = userId, projectId = ProjectId(maliciousId))
		}

		val expectedSegments = listOf("api", "project", userId.toString(), maliciousId, "project_data")
		assertEquals(expectedSegments, url.rawSegments.filter { it.isNotEmpty() })
		assertFalse(
			url.rawSegments.any { it == ".." },
			"Outbound path must not contain a literal '..' dot-segment: ${url.encodedPath}"
		)
	}

	@Test
	fun `slash in project id does not create extra segments for ServerProjectApi`() = runTest {
		val client = mockClient()
		val api = ServerProjectApi(client, globalSettingsStore, json, TestStrRes())

		val maliciousId = "a/b/c"

		val url = captureUrlOf {
			api.downloadEntity(
				projectId = ProjectId(maliciousId),
				entityId = 7,
				localHash = null,
				syncId = "sync-1",
			)
		}
		val expectedSegments = listOf("api", "project", userId.toString(), maliciousId, "download_entity", "7")
		assertEquals(expectedSegments, url.rawSegments.filter { it.isNotEmpty() })
	}

	@Test
	fun `slash in project id does not create extra segments for WritingActivityApi`() = runTest {
		val client = mockClient()
		val api = WritingActivityApi(client, globalSettingsStore, TestStrRes())

		val maliciousId = "x/../y"

		val url = captureUrlOf {
			api.getWritingActivity(userId = userId, projectId = ProjectId(maliciousId))
		}
		val expectedSegments = listOf("api", "project", userId.toString(), maliciousId, "writing_activity")
		assertEquals(expectedSegments, url.rawSegments.filter { it.isNotEmpty() })
		assertFalse(url.rawSegments.any { it == ".." })
	}
}
