package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.preDeletedProject1
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

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
			val newProjectId = ProjectId.randomUUID()
			val createProjectResponse = post(api("projects/$userId/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, beginSyncResponseBody.syncId)
				}
				parameter("projectName", newProjectName)
				parameter("projectId", newProjectId.id)
			}
			assertEquals(HttpStatusCode.OK, createProjectResponse.status)
			val createdProject: CreateProjectResponse = createProjectResponse.body()
			assertFalse(createdProject.alreadyExisted)
			assertEquals(newProjectId, createdProject.projectId)

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

			// A fresh sync must reflect the create and the delete
			val verifySyncResponse = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, verifySyncResponse.status)
			val verifyBody: BeginProjectsSyncResponse = verifySyncResponse.body()

			assertEquals(
				setOf(ApiProjectDefinition(newProjectName, createdProject.projectId)),
				verifyBody.projects,
			)
			assertTrue(
				ProjectId.fromUUID(TestDataSet1.project1.uuid) in verifyBody.deletedProjects,
				"The deleted project must be reported as deleted on the next sync",
			)

			val verifyEndSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, verifyBody.syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, verifyEndSyncResponse.status)
		}
	}

	@Test
	fun `Projects Sync - All account endpoints accept POST`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			// Begin Sync
			val beginSyncResponse = post(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
			val syncId = beginSyncResponse.body<BeginProjectsSyncResponse>().syncId

			// Create Project
			val newProjectName = "New Project"
			val createProjectResponse = post(api("projects/$userId/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectName", newProjectName)
				parameter("projectId", ProjectId.randomUUID().id)
			}
			assertEquals(HttpStatusCode.OK, createProjectResponse.status)

			// Rename Project
			val renameProjectResponse = post(api("projects/$userId/rename")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", TestDataSet1.project1.uuid)
				parameter("projectName", "Renamed Project")
			}
			assertEquals(HttpStatusCode.OK, renameProjectResponse.status)

			// Delete Project
			val deleteProjectResponse = post(api("projects/$userId/delete")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", TestDataSet1.project1.uuid)
			}
			assertEquals(HttpStatusCode.OK, deleteProjectResponse.status)

			// End sync
			val endSyncResponse = post(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
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

	@Test
	fun `Project Sync - Rename onto a name another project still holds is a conflict`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val beginSyncResponse = get(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
			val syncId = beginSyncResponse.body<BeginProjectsSyncResponse>().syncId

			val occupiedName = "Occupied Project"
			val createResponse = post(api("projects/$userId/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectName", occupiedName)
				parameter("projectId", ProjectId.randomUUID().id)
			}
			assertEquals(HttpStatusCode.OK, createResponse.status)
			val occupyingProjectId = createResponse.body<CreateProjectResponse>().projectId

			val renameResponse = get(api("projects/$userId/rename")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", TestDataSet1.project1.uuid)
				parameter("projectName", occupiedName)
			}
			assertEquals(HttpStatusCode.Conflict, renameResponse.status)

			// Every client sends its device locale, and this key has no translation yet.
			val translatedRenameResponse = get(api("projects/$userId/rename")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
					append(HttpHeaders.AcceptLanguage, "de")
				}
				parameter("projectId", TestDataSet1.project1.uuid)
				parameter("projectName", occupiedName)
			}
			assertEquals(HttpStatusCode.Conflict, translatedRenameResponse.status)

			// Freeing the name first is what the client's delete-before-rename ordering guarantees.
			val deleteResponse = get(api("projects/$userId/delete")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", occupyingProjectId.id)
			}
			assertEquals(HttpStatusCode.OK, deleteResponse.status)

			val retryRenameResponse = get(api("projects/$userId/rename")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectId", TestDataSet1.project1.uuid)
				parameter("projectName", occupiedName)
			}
			assertEquals(HttpStatusCode.OK, retryRenameResponse.status)

			val endSyncResponse = get(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)
		}
	}

	@Test
	fun `Project Sync - Create honours the client id contract`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val otherAccount = TestAccount(email = "other@test.com", password = "password123!@#")
		E2eTestData.createAccount(otherAccount, database)
		val otherUserId = database.serverDatabase.accountQueries
			.findAccount(otherAccount.email).executeAsOne().id
		val otherProjectId = Uuid.random()
		E2eTestData.createProject(TestProject("Their Project", otherProjectId, otherUserId), database)
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		client().apply {
			val beginSyncResponse = post(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, beginSyncResponse.status)
			val syncId = beginSyncResponse.body<BeginProjectsSyncResponse>().syncId

			suspend fun create(name: String, projectId: String?) = post(api("projects/$userId/create")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
				parameter("projectName", name)
				if (projectId != null) parameter("projectId", projectId)
			}

			val existingId = ProjectId.fromUUID(TestDataSet1.project1.uuid)

			val repeat = create(TestDataSet1.project1.name, existingId.id)
			assertEquals(HttpStatusCode.OK, repeat.status)
			assertEquals(CreateProjectResponse(existingId, alreadyExisted = true), repeat.body())

			val adopted = create(TestDataSet1.project1.name, ProjectId.randomUUID().id)
			assertEquals(HttpStatusCode.OK, adopted.status)
			assertEquals(CreateProjectResponse(existingId, alreadyExisted = true), adopted.body())

			val tombstoned = create("Resurrected", preDeletedProject1.toString())
			assertEquals(HttpStatusCode.Conflict, tombstoned.status)
			assertEquals(ApiErrorCode.PROJECT_DELETED, tombstoned.body<HttpResponseError>().errorCode)

			val taken = create("Mine Now", otherProjectId.toString())
			assertEquals(HttpStatusCode.Conflict, taken.status)
			assertEquals(ApiErrorCode.PROJECT_ID_TAKEN, taken.body<HttpResponseError>().errorCode)

			assertEquals(HttpStatusCode.BadRequest, create("No Id", null).status)
			assertEquals(HttpStatusCode.BadRequest, create("Bad Id", "not-a-uuid").status)

			val endSyncResponse = post(api("projects/$userId/end_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
					append(HEADER_SYNC_ID, syncId)
				}
			}
			assertEquals(HttpStatusCode.OK, endSyncResponse.status)

			val verifySyncResponse = post(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${authToken.auth}")
				}
			}
			assertEquals(
				setOf(ApiProjectDefinition(TestDataSet1.project1.name, existingId)),
				verifySyncResponse.body<BeginProjectsSyncResponse>().projects,
				"No rejected create may leave a project behind",
			)
		}
	}
}
