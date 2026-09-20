package server

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createNetworkJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Synced models gain fields without a protocol bump, so a client has to drop fields a newer build
 * wrote rather than failing the decode. See "Server storage is shape-agnostic" in
 * `SYNCING-PROTOCOL.md`.
 */
class NetworkJsonForwardCompatTest : BaseTest() {

	private val userId = 42L
	private val json = createNetworkJsonSerializer()
	private lateinit var globalSettingsStore: GlobalSettingsStore

	/** A payload written by a build that knows fields this one does not. */
	private val futurePayload = """
		{"data":{"authorName":"Ada","theme":null,"wordCountGoal":null,"tags":[],"language":"nb",
		"encyclopediaDictionary":true,"unknownFutureField":"whatever"},"hash":"CJjmUTfFWaIl8YePLhBLSQ"}
	""".trimIndent().replace("\n", "")

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

	@Test
	fun `the network serializer drops fields it does not know`() {
		val dto = json.decodeFromString<ProjectDataDto>(futurePayload)

		assertEquals("Ada", dto.data.authorName)
		assertEquals("CJjmUTfFWaIl8YePLhBLSQ", dto.hash)
	}

	@Test
	fun `downloading project data written by a newer build succeeds`() = runTest {
		val engine = MockEngine {
			respond(
				content = futurePayload,
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
			)
		}
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(json) }
		}
		val api = ProjectDataApi(client, globalSettingsStore, json, TestStrRes())

		val result = api.getProjectData(userId, ProjectId("project-uuid"))

		assertTrue(result.isSuccess)
		assertEquals("Ada", result.getOrThrow()?.data?.authorName)
	}

	/** prettyPrint would put tabs and newlines in every request body. */
	@Test
	fun `the network serializer does not pretty print`() {
		assertFalse(json.configuration.prettyPrint)
	}
}
