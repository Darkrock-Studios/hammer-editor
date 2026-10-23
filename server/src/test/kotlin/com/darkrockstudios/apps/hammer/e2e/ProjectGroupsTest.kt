package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.EndToEndTest
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProjectGroupsTest : EndToEndTest() {

	private val userId = 1L
	private val project1Id = ProjectId.fromUUID(TestDataSet1.project1.uuid)

	/** One account sync session against the server under test. */
	private inner class Session(private val client: HttpClient, private val auth: String) {
		lateinit var syncId: String

		private fun HttpRequestBuilder.authHeaders(withSyncId: Boolean = true) {
			headers {
				append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
				append("Authorization", "Bearer $auth")
				if (withSyncId) append(HEADER_SYNC_ID, syncId)
			}
		}

		suspend fun begin(): BeginProjectsSyncResponse {
			val response = client.post(api("projects/$userId/begin_sync")) { authHeaders(withSyncId = false) }
			assertEquals(HttpStatusCode.OK, response.status)
			return response.body<BeginProjectsSyncResponse>().also { syncId = it.syncId }
		}

		suspend fun end() {
			assertEquals(HttpStatusCode.OK, client.post(api("projects/$userId/end_sync")) { authHeaders() }.status)
		}

		suspend fun upsert(group: ApiGroupDefinition, withSyncId: Boolean = true): HttpResponse =
			client.post(api("projects/$userId/group/upsert")) {
				authHeaders(withSyncId)
				contentType(ContentType.Application.Json)
				setBody(group)
			}

		suspend fun upsertOk(group: ApiGroupDefinition): UpsertGroupResponse {
			val response = upsert(group)
			assertEquals(HttpStatusCode.OK, response.status)
			return response.body()
		}

		suspend fun deleteGroup(groupId: GroupId): HttpResponse =
			client.post(api("projects/$userId/group/delete")) {
				authHeaders()
				parameter("groupId", groupId.id)
			}

		suspend fun move(projectId: ProjectId, groupId: GroupId?): HttpResponse =
			client.post(api("projects/$userId/move")) {
				authHeaders()
				parameter("projectId", projectId.id)
				if (groupId != null) parameter("groupId", groupId.id)
			}

		suspend fun create(name: String, projectId: ProjectId, groupId: GroupId?): CreateProjectResponse {
			val response = client.post(api("projects/$userId/create")) {
				authHeaders()
				parameter("projectName", name)
				parameter("projectId", projectId.id)
				if (groupId != null) parameter("groupId", groupId.id)
			}
			assertEquals(HttpStatusCode.OK, response.status)
			return response.body()
		}
	}

	private suspend fun startServer(): Session {
		val database = database()
		createTestServer(SERVER_EMPTY, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()
		return Session(client(), authToken.auth)
	}

	private suspend fun HttpResponse.errorCode(): String? = body<HttpResponseError>().errorCode

	@Test
	fun `Groups - Golden path`(): Unit = runBlocking {
		val session = startServer()
		session.begin()

		val series = GroupId.randomUUID()
		val trilogy = GroupId.randomUUID()
		assertEquals(UpsertGroupResponse(series, alreadyExisted = false), session.upsertOk(ApiGroupDefinition(series, "Series")))
		assertEquals(
			UpsertGroupResponse(trilogy, alreadyExisted = false),
			session.upsertOk(ApiGroupDefinition(trilogy, "Trilogy", series, "three books")),
		)

		val newProject = ProjectId.randomUUID()
		val created = session.create("Book One", newProject, trilogy)
		assertEquals(CreateProjectResponse(newProject, alreadyExisted = false, groupId = trilogy), created)

		assertEquals(HttpStatusCode.OK, session.move(project1Id, series).status)
		session.end()

		val verify = session.begin()
		assertEquals(
			setOf(
				ApiGroupDefinition(series, "Series"),
				ApiGroupDefinition(trilogy, "Trilogy", series, "three books"),
			),
			verify.groups,
		)
		assertEquals(
			setOf(
				ApiProjectDefinition(TestDataSet1.project1.name, project1Id, series),
				ApiProjectDefinition("Book One", newProject, trilogy),
			),
			verify.projects,
		)

		assertEquals(HttpStatusCode.OK, session.move(project1Id, null).status)
		val adopted = session.create(TestDataSet1.project1.name, ProjectId.randomUUID(), trilogy)
		assertEquals(CreateProjectResponse(project1Id, alreadyExisted = true, groupId = null), adopted)
		session.end()
	}

	@Test
	fun `Groups - A new group adopts a same-name sibling`(): Unit = runBlocking {
		val session = startServer()
		session.begin()

		val original = GroupId.randomUUID()
		session.upsertOk(ApiGroupDefinition(original, "Trilogy"))

		val adopted = session.upsertOk(ApiGroupDefinition(GroupId.randomUUID(), "TRILOGY"))
		assertEquals(UpsertGroupResponse(original, alreadyExisted = true), adopted)

		val repeated = session.upsertOk(ApiGroupDefinition(original, "Trilogy"))
		assertEquals(UpsertGroupResponse(original, alreadyExisted = true), repeated)
		session.end()

		assertEquals(setOf(ApiGroupDefinition(original, "Trilogy")), session.begin().groups)
		session.end()
	}

	@Test
	fun `Groups - Renaming onto a sibling is a conflict`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		session.upsertOk(ApiGroupDefinition(GroupId.randomUUID(), "One"))
		val two = GroupId.randomUUID()
		session.upsertOk(ApiGroupDefinition(two, "Two"))

		val response = session.upsert(ApiGroupDefinition(two, "one"))

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.GROUP_NAME_TAKEN, response.errorCode())
		session.end()
	}

	@Test
	fun `Groups - Moving a group inside its descendant is a conflict`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		val top = GroupId.randomUUID()
		val child = GroupId.randomUUID()
		session.upsertOk(ApiGroupDefinition(top, "Top"))
		session.upsertOk(ApiGroupDefinition(child, "Child", top))

		val response = session.upsert(ApiGroupDefinition(top, "Top", child))

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.GROUP_CYCLE, response.errorCode())
		session.end()
	}

	@Test
	fun `Groups - Nesting is capped`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		var parent: GroupId? = null
		repeat(MAX_GROUP_NESTING_DEPTH) { level ->
			val id = GroupId.randomUUID()
			assertFalse(session.upsertOk(ApiGroupDefinition(id, "Level $level", parent)).alreadyExisted)
			parent = id
		}

		val response = session.upsert(ApiGroupDefinition(GroupId.randomUUID(), "Too Deep", parent))

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.GROUP_TOO_DEEP, response.errorCode())
		session.end()
	}

	@Test
	fun `Groups - Deleting a group moves its contents up to its parent`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		val grandparent = GroupId.randomUUID()
		val parent = GroupId.randomUUID()
		val child = GroupId.randomUUID()
		session.upsertOk(ApiGroupDefinition(grandparent, "Series"))
		session.upsertOk(ApiGroupDefinition(parent, "Trilogy", grandparent))
		session.upsertOk(ApiGroupDefinition(child, "Drafts", parent))
		assertEquals(HttpStatusCode.OK, session.move(project1Id, parent).status)

		assertEquals(HttpStatusCode.OK, session.deleteGroup(parent).status)
		assertEquals(HttpStatusCode.OK, session.deleteGroup(parent).status)
		session.end()

		val verify = session.begin()
		assertEquals(
			setOf(ApiGroupDefinition(grandparent, "Series"), ApiGroupDefinition(child, "Drafts", grandparent)),
			verify.groups,
		)
		assertEquals(
			setOf(ApiProjectDefinition(TestDataSet1.project1.name, project1Id, grandparent)),
			verify.projects,
		)
		session.end()
	}

	@Test
	fun `Groups - Deleting a group whose child collides at the parent is a conflict`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		val parent = GroupId.randomUUID()
		session.upsertOk(ApiGroupDefinition(GroupId.randomUUID(), "Drafts"))
		session.upsertOk(ApiGroupDefinition(parent, "Trilogy"))
		session.upsertOk(ApiGroupDefinition(GroupId.randomUUID(), "drafts", parent))

		val response = session.deleteGroup(parent)

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.GROUP_NAME_TAKEN, response.errorCode())
		session.end()

		assertEquals(3, session.begin().groups.size)
		session.end()
	}

	@Test
	fun `Groups - Writes require the session's sync id`(): Unit = runBlocking {
		val session = startServer()
		session.begin()
		val group = ApiGroupDefinition(GroupId.randomUUID(), "Trilogy")

		assertEquals(HttpStatusCode.BadRequest, session.upsert(group, withSyncId = false).status)
		val realSyncId = session.syncId
		session.syncId = "not-the-session"
		assertEquals(HttpStatusCode.BadRequest, session.upsert(group).status)
		assertEquals(HttpStatusCode.BadRequest, session.deleteGroup(group.uuid).status)
		assertEquals(HttpStatusCode.BadRequest, session.move(project1Id, null).status)
		session.syncId = realSyncId
		session.end()

		assertTrue(session.begin().groups.isEmpty())
		session.end()
	}

	@Test
	fun `Groups - An id another account holds is a conflict`(): Unit = runBlocking {
		val database = database()
		val session = startServer()
		val otherAccount = TestAccount(email = "other@test.com", password = "password123!@#")
		E2eTestData.createAccount(otherAccount, database)
		val otherUserId = database.serverDatabase.accountQueries.findAccount(otherAccount.email).executeAsOne().id
		val theirs = Uuid.random()
		E2eTestData.createGroup(otherUserId, theirs, "Theirs", database)

		session.begin()
		val response = session.upsert(ApiGroupDefinition(GroupId.fromUUID(theirs), "Mine"))

		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.GROUP_ID_TAKEN, response.errorCode())
		session.end()
	}

	@Test
	fun `Groups - Moving an unknown project is not found`(): Unit = runBlocking {
		val session = startServer()
		session.begin()

		assertEquals(HttpStatusCode.NotFound, session.move(ProjectId.randomUUID(), null).status)
		session.end()
	}
}
