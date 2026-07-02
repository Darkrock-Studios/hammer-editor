package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.preDeletedProject1
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectsTest : EndToEndTest() {
	@Test
	fun `Project - Begin Sync - No auth`(): Unit = runBlocking {
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database())
		doStartServer()

		client().apply {
			val response = get(api("projects/1/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				}
			}

			assertEquals(HttpStatusCode.Unauthorized, response.status)
		}
	}

	@Test
	fun `Project Sync - Golden Path`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// Begin Sync
			val beginSyncResponse = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}

			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)

			val beginSyncResponseBody: BeginProjectsSyncResponse = beginSyncResponse.body()
			assertTrue(beginSyncResponseBody.syncId.isNotEmpty())
			assertEquals(1, beginSyncResponseBody.projects.size)
			assertEquals(1, beginSyncResponseBody.deletedProjects.size)

			beginSyncResponseBody.projects.first().let { project ->
				assertEquals(TestDataSet1.project1.uuid.toString(), project.uuid.id)
				assertEquals(TestDataSet1.project1.name, project.name)
			}

			beginSyncResponseBody.deletedProjects.first().let { uuid ->
				assertEquals(preDeletedProject1.toString(), uuid.id)
			}

			// Create Project
			val newProjectName = "New Project"
			val createProjectResponse = get(api("projects/$userId/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
				parameter("projectName", newProjectName)
			}
			assertEquals(HttpStatusCode.OK, createProjectResponse.status)

			// Delete Project
			val deleteProjectResponse = get(api("projects/$userId/delete")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}

				parameter("projectId", TestDataSet1.project1.uuid)
			}
			assertEquals(HttpStatusCode.OK, deleteProjectResponse.status)

			// End sync
			val endSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
			}

			assertEquals(HttpStatusCode.OK, endSyncResponse.status)
		}
	}

	@Test
	fun `Projects Sync - Beginning again reclaims the same install's existing session`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val beginSyncResponse1 = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse1.status)
			val syncId1 = beginSyncResponse1.body<BeginProjectsSyncResponse>().syncId

			// A leaked/stale session must not lock the owner out: beginning again succeeds and
			// reclaims the session with a fresh sync ID.
			val beginSyncResponse2 = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse2.status)
			val syncId2 = beginSyncResponse2.body<BeginProjectsSyncResponse>().syncId
			assertNotEquals(syncId1, syncId2)

			// The reclaimed (old) sync ID is no longer valid.
			val staleEndSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId1)
				}
			}
			assertNotEquals(HttpStatusCode.OK, staleEndSyncResponse.status)

			// The new session works.
			val endSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId2)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)
		}
	}

	@Test
	fun `Projects Sync - Begin from a different install is rejected while a session is active`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authTokenA = createAuthToken(userId, "install-a", database = database, tokenHasher = tokenHasher())
		val authTokenB = createAuthToken(userId, "install-b", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val beginSyncResponseA = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authTokenA.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponseA.status)
			val syncIdA = beginSyncResponseA.body<BeginProjectsSyncResponse>().syncId

			// Another install can't steal the active session.
			val beginSyncResponseB = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authTokenB.auth}")
				}
			}
			assertEquals(HttpStatusCode.BadRequest, beginSyncResponseB.status)

			// The original session is untouched.
			val endSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authTokenA.auth}")
					append(HEADER_SYNC_ID, syncIdA)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)
		}
	}

	@Test
	fun `Project Sync - Rename Project`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// Begin Sync
			val beginSyncResponse = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
			val beginSyncResponseBody: BeginProjectsSyncResponse = beginSyncResponse.body()

			// Rename Project
			val newProjectName = "New Project Name"
			val renameProjectResponse = get(api("projects/$userId/rename")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
				parameter("projectId", TestDataSet1.project1.uuid)
				parameter("projectName", newProjectName)
			}
			assertEquals(HttpStatusCode.OK, renameProjectResponse.status)

			// End sync
			val endSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)

			// Now verify the project is renamed
			val beginSyncResponse2 = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse2.status)
			val beginSyncResponseBody2: BeginProjectsSyncResponse = beginSyncResponse2.body()

			// Find the now renamed project
			val uuid = ProjectId.fromUUID(TestDataSet1.project1.uuid)
			val renamedProject = beginSyncResponseBody2.projects.find { it.uuid == uuid }
			assertEquals(ApiProjectDefinition(newProjectName, uuid), renamedProject)

			val endSyncResponse2 = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody2.syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse2.status)
		}
	}
}