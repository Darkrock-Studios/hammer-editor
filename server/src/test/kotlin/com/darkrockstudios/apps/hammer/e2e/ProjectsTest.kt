package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.preDeletedProject1
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
		TestDataSet1.createFullDataset(database)
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database)
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
	fun `Project Sync - Rename Project`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database)
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database)
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
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse2.status)
		}
	}
}