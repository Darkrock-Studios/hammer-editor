package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeRequest
import com.darkrockstudios.apps.hammer.base.http.ProjectsSyncProbeResponse
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsProbeTest : ProjectSyncTestBase() {

	private val userId = 1L
	private val project1Id = ProjectId.fromUUID(TestDataSet1.project1.uuid)

	/**
	 * The hash a synced client would compute for project1: every live entity's hash plus the
	 * default project-data hash (the fixture seeds no project_data row). The server must arrive at
	 * the same value from its own stored state — that agreement is the whole point of the probe.
	 */
	private fun clientProjectHash(): String {
		val entityHashes = TestDataSet1.user1Project1Entities.map { EntityHash(it.id, it.hash()) }
		return ProjectContentHasher.hash(entityHashes, ProjectDataHasher.hash(ProjectData()))
	}

	private fun upToDateClientState() = ClientEntityState(
		entities = TestDataSet1.user1Project1Entities.map { EntityHash(it.id, it.hash()) }.toSet(),
	)

	private suspend fun HttpClient.probeRequest(
		authToken: Token?,
		request: ProjectsSyncProbeRequest,
	): HttpResponse =
		post(api("projects/$userId/sync_probe")) {
			headers {
				append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				if (authToken != null) append("Authorization", "Bearer ${authToken.auth}")
			}
			contentType(ContentType.Application.Json)
			setBody(request)
		}

	@Test
	fun `probe reports a project unchanged when the client and server hashes agree`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val response = probeRequest(authToken, ProjectsSyncProbeRequest(listOf(ProjectHashItem(project1Id, clientProjectHash()))))
			assertEquals(HttpStatusCode.OK, response.status)

			val unchanged = response.body<ProjectsSyncProbeResponse>().unchangedProjects
			assertTrue(project1Id in unchanged, "a genuinely in-sync project must come back unchanged")
		}
	}

	@Test
	fun `probe omits a project whose hash differs`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val response = probeRequest(authToken, ProjectsSyncProbeRequest(listOf(ProjectHashItem(project1Id, "stale-hash"))))
			assertEquals(HttpStatusCode.OK, response.status)

			val unchanged = response.body<ProjectsSyncProbeResponse>().unchangedProjects
			assertFalse(project1Id in unchanged, "a project whose hash differs must not be reported unchanged")
		}
	}

	@Test
	fun `probe omits a project with an in-flight sync session`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// Open a project sync session, then probe with the otherwise-matching hash.
			val began = projectSynchronizationBegan(userId, authToken, upToDateClientState())

			val response = probeRequest(authToken, ProjectsSyncProbeRequest(listOf(ProjectHashItem(project1Id, clientProjectHash()))))
			assertEquals(HttpStatusCode.OK, response.status)

			val unchanged = response.body<ProjectsSyncProbeResponse>().unchangedProjects
			assertFalse(project1Id in unchanged, "a project being synced must not be reported unchanged even if hashes match")

			endSyncRequest(userId, authToken, began)
		}
	}

	@Test
	fun `probe rejects an unauthenticated request`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		doStartServer()

		client().apply {
			val response = probeRequest(authToken = null, request = ProjectsSyncProbeRequest(emptyList()))
			assertEquals(HttpStatusCode.Unauthorized, response.status)
		}
	}

	@Test
	fun `probe returns 400 for a malformed body`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val response = post(api("projects/$userId/sync_probe")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
				setBody(TextContent("{ not valid json", ContentType.Application.Json))
			}
			assertEquals(HttpStatusCode.BadRequest, response.status)
		}
	}
}
