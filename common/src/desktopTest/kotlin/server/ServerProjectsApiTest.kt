package server

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.UpsertGroupResponse
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mutating account endpoints are POST only; a 404 is a failure, never a retry. */
class ServerProjectsApiTest : BaseTest() {

	private val userId = 42L
	private val json = Json { ignoreUnknownKeys = true }
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

	private fun server() = MockEngine { request ->
		when {
			request.method != HttpMethod.Post -> respond("", HttpStatusCode.MethodNotAllowed)
			request.url.encodedPath.endsWith("/create") -> respond(
				content = json.encodeToString(
					CreateProjectResponse(ProjectId("uuid-local"), alreadyExisted = false)
				),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
			)
			request.url.encodedPath.endsWith("/group/upsert") -> respond(
				content = json.encodeToString(UpsertGroupResponse(GroupId("group-1"), alreadyExisted = false)),
				status = HttpStatusCode.OK,
				headers = jsonHeaders,
			)
			else -> respond("Okay", HttpStatusCode.OK)
		}
	}

	private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

	private fun createApi(engine: MockEngine): ServerProjectsApi {
		val client = HttpClient(engine) {
			install(ContentNegotiation) { json(json) }
		}
		return ServerProjectsApi(client, globalSettingsStore, TestStrRes())
	}

	@Test
	fun `end sync, delete and rename each send a single POST with the sync id`() = runTest {
		val engine = server()
		val api = createApi(engine)

		assertTrue(api.endProjectsSync("sync-1").isSuccess)
		assertTrue(api.deleteProject(ProjectId("uuid-1"), "sync-1").isSuccess)
		assertTrue(api.renameProject(ProjectId("uuid-1"), "sync-1", "New Name").isSuccess)

		val requests = engine.requestHistory
		assertEquals(
			listOf("/api/projects/42/end_sync", "/api/projects/42/delete", "/api/projects/42/rename"),
			requests.map { it.url.encodedPath },
		)
		assertTrue(requests.all { it.method == HttpMethod.Post })
		assertTrue(requests.all { it.headers[HEADER_SYNC_ID] == "sync-1" })
		assertEquals("uuid-1", requests[1].url.parameters["projectId"])
		assertEquals("New Name", requests[2].url.parameters["projectName"])
	}

	@Test
	fun `create sends the client's project id in a single POST`() = runTest {
		val engine = server()
		val api = createApi(engine)

		val created = api.createProject("New Project", ProjectId("uuid-local"), "sync-1")

		assertTrue(created.isSuccess)
		assertEquals(ProjectId("uuid-local"), created.getOrThrow().projectId)
		val request = engine.requestHistory.single()
		assertEquals(HttpMethod.Post, request.method)
		assertEquals("uuid-local", request.url.parameters["projectId"])
		assertEquals("New Project", request.url.parameters["projectName"])
	}

	@Test
	fun `a 404 is a failure with no retry`() = runTest {
		val engine = MockEngine { _ -> respond("", HttpStatusCode.NotFound) }
		val api = createApi(engine)

		val result = api.endProjectsSync("sync-1")

		assertTrue(result.isFailure)
		assertEquals(listOf(HttpMethod.Post), engine.requestHistory.map { it.method })
	}

	@Test
	fun `create sends a group id only when one is given`() = runTest {
		val engine = server()
		val api = createApi(engine)

		api.createProject("Top", ProjectId("uuid-top"), "sync-1")
		api.createProject("Nested", ProjectId("uuid-nested"), "sync-1", GroupId("group-1"))

		val (top, nested) = engine.requestHistory
		assertFalse(top.url.parameters.contains("groupId"))
		assertEquals("group-1", nested.url.parameters["groupId"])
	}

	@Test
	fun `upsert group posts the group as JSON with the sync id`() = runTest {
		val engine = server()
		val api = createApi(engine)
		val group = ApiGroupDefinition(GroupId("group-1"), "Trilogy", GroupId("parent-1"), "Three books")

		val result = api.upsertGroup(group, "sync-1")

		assertEquals(UpsertGroupResponse(GroupId("group-1"), alreadyExisted = false), result.getOrThrow())
		val request = engine.requestHistory.single()
		assertEquals("/api/projects/42/group/upsert", request.url.encodedPath)
		assertEquals(HttpMethod.Post, request.method)
		assertEquals("sync-1", request.headers[HEADER_SYNC_ID])
		val sent = json.decodeFromString<ApiGroupDefinition>(request.body.toByteArray().decodeToString())
		assertEquals(group, sent)
	}

	@Test
	fun `upsert group surfaces the error code of a 409`() = runTest {
		val engine = MockEngine { _ ->
			respond(
				content = json.encodeToString(
					HttpResponseError(error = "Conflict", displayMessage = "taken", errorCode = ApiErrorCode.GROUP_NAME_TAKEN)
				),
				status = HttpStatusCode.Conflict,
				headers = jsonHeaders,
			)
		}
		val api = createApi(engine)

		val result = api.upsertGroup(ApiGroupDefinition(GroupId("group-1"), "Trilogy"), "sync-1")

		val error = result.exceptionOrNull() as HttpFailureException
		assertEquals(HttpStatusCode.Conflict, error.statusCode)
		assertEquals(ApiErrorCode.GROUP_NAME_TAKEN, error.error.errorCode)
	}

	@Test
	fun `delete group and move project send their ids with the sync id`() = runTest {
		val engine = server()
		val api = createApi(engine)

		assertTrue(api.deleteGroup(GroupId("group-1"), "sync-1").isSuccess)
		assertTrue(api.moveProject(ProjectId("uuid-1"), GroupId("group-2"), "sync-1").isSuccess)

		val (delete, move) = engine.requestHistory
		assertEquals("/api/projects/42/group/delete", delete.url.encodedPath)
		assertEquals("group-1", delete.url.parameters["groupId"])
		assertEquals("/api/projects/42/move", move.url.encodedPath)
		assertEquals("uuid-1", move.url.parameters["projectId"])
		assertEquals("group-2", move.url.parameters["groupId"])
		assertTrue(engine.requestHistory.all { it.method == HttpMethod.Post && it.headers[HEADER_SYNC_ID] == "sync-1" })
	}

	@Test
	fun `moving a project to the top level omits the group id`() = runTest {
		val engine = server()
		val api = createApi(engine)

		api.moveProject(ProjectId("uuid-1"), groupId = null, syncId = "sync-1")

		assertNull(engine.requestHistory.single().url.parameters["groupId"])
	}
}
