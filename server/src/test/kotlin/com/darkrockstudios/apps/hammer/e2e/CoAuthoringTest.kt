package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.TestAccount
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Two accounts on one project: the owner (TestDataSet1's account 1) and a co-author whose
 * project_member row is inserted directly, since no route adds members yet.
 */
class CoAuthoringTest : ProjectSyncTestBase() {

	private val ownerId = 1L
	private val memberId = 2L
	private val strangerId = 3L
	private val projectRowId = 1L

	private lateinit var ownerToken: Token
	private lateinit var memberToken: Token

	private val emptyState = ClientEntityState(entities = emptySet())

	private fun setUpProject(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		E2eTestData.createAccount(TestAccount("member@test.com", "password123!@#"), database)
		E2eTestData.createAccount(TestAccount("stranger@test.com", "password123!@#"), database)
		database.serverDatabase.projectMemberQueries.insertMember(projectRowId, memberId)

		ownerToken = createAuthToken(ownerId, "owner-install", database = database, tokenHasher = tokenHasher())
		memberToken = createAuthToken(memberId, "member-install", database = database, tokenHasher = tokenHasher())
		doStartServer()
	}

	private suspend fun HttpClient.beginAccountSync(userId: Long, token: Token): String {
		val response = post(api("projects/$userId/begin_sync")) { authHeaders(token) }
		assertEquals(HttpStatusCode.OK, response.status)
		return response.body<BeginProjectsSyncResponse>().syncId
	}

	private suspend fun HttpClient.accountRequest(
		path: String,
		userId: Long,
		token: Token,
		syncId: String,
		vararg params: Pair<String, String>,
	): HttpResponse = post(api("projects/$userId/$path")) {
		authHeaders(token)
		headers { append(HEADER_SYNC_ID, syncId) }
		parameter("projectId", TestDataSet1.project1.uuid)
		params.forEach { (key, value) -> parameter(key, value) }
	}

	private fun HttpRequestBuilder.authHeaders(token: Token) = headers {
		append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
		append("Authorization", "Bearer ${token.auth}")
	}

	@Test
	fun `a member syncs the owner's project and reads the owner's entities`(): Unit = runBlocking {
		setUpProject()

		client().apply {
			val began = projectSynchronizationBegan(memberId, memberToken, emptyState)
			assertEquals(TestDataSet1.user1Project1Entities.map { it.id }.sorted(), began.idSequence.sorted())
			assertEquals(setOf(7), began.deletedIds)

			val expected = TestDataSet1.user1Project1Entities.first { it.id == 1 }
			val downloaded = downloadEntity<ApiProjectEntity.SceneEntity>(memberId, memberToken, began.syncId, 1, null)
			assertEquals(expected, downloaded)

			endSyncRequest(memberId, memberToken, began)
		}
	}

	@Test
	fun `an entity stored by a member decrypts for the owner, and the reverse`(): Unit = runBlocking {
		setUpProject()
		val fromMember = E2eTestData.createTestScene(20)
		val fromOwner = E2eTestData.createTestScene(21)

		client().apply {
			val memberSync = projectSynchronizationBegan(memberId, memberToken, emptyState)
			uploadEntityRequest(memberId, memberToken, memberSync.syncId, fromMember, "")
			endSyncRequest(memberId, memberToken, memberSync)

			// Stored as the owner's row, not a shadow row under the member.
			val entities = database().serverDatabase.storyEntityQueries
			assertNotNull(entities.getEntity(ownerId, projectRowId, 20).executeAsOneOrNull())

			val ownerSync = projectSynchronizationBegan(ownerId, ownerToken, emptyState)
			assertEquals(
				fromMember,
				downloadEntity<ApiProjectEntity.SceneEntity>(ownerId, ownerToken, ownerSync.syncId, 20, null),
			)
			uploadEntityRequest(ownerId, ownerToken, ownerSync.syncId, fromOwner, "")
			endSyncRequest(ownerId, ownerToken, ownerSync)

			val memberSync2 = projectSynchronizationBegan(memberId, memberToken, emptyState)
			assertEquals(
				fromOwner,
				downloadEntity<ApiProjectEntity.SceneEntity>(memberId, memberToken, memberSync2.syncId, 21, null),
			)
			endSyncRequest(memberId, memberToken, memberSync2)
		}
	}

	@Test
	fun `a non-member is refused with not_member and an unknown id is gone`(): Unit = runBlocking {
		setUpProject()
		val strangerToken =
			createAuthToken(strangerId, "stranger-install", database = database(), tokenHasher = tokenHasher())

		client().apply {
			val refused = projectSynchronizationBeganRequest(strangerId, strangerToken, emptyState)
			assertEquals(HttpStatusCode.Forbidden, refused.status)
			assertEquals(ApiErrorCode.NOT_MEMBER, refused.body<HttpResponseError>().errorCode)

			val unknown = post(api("project/$memberId/${Uuid.random()}/begin_sync")) { authHeaders(memberToken) }
			assertEquals(HttpStatusCode.Gone, unknown.status)
		}
	}

	@Test
	fun `a member is refused once the owner is soft-deleted`(): Unit = runBlocking {
		setUpProject()
		database().serverDatabase.accountQueries.markDeleted(deletedAt = Clock.System.now(), userId = ownerId)

		client().apply {
			val refused = projectSynchronizationBeganRequest(memberId, memberToken, emptyState)
			assertEquals(HttpStatusCode.Forbidden, refused.status)
			assertEquals(ApiErrorCode.NOT_MEMBER, refused.body<HttpResponseError>().errorCode)
		}
	}

	@Test
	fun `an inactive member is turned away before membership is checked`(): Unit = runBlocking {
		setUpProject()
		database().serverDatabase.accountQueries.markDeleted(deletedAt = Clock.System.now(), userId = memberId)

		client().apply {
			val refused = projectSynchronizationBeganRequest(memberId, memberToken, emptyState)
			assertEquals(HttpStatusCode.Unauthorized, refused.status)
		}
	}

	@Test
	fun `two accounts cannot hold a project session at once, and same-install reclaim still works`(): Unit =
		runBlocking {
			setUpProject()

			client().apply {
				val ownerSync = projectSynchronizationBegan(ownerId, ownerToken, emptyState)
				val memberRefused = projectSynchronizationBeganRequest(memberId, memberToken, emptyState)
				assertEquals(HttpStatusCode.BadRequest, memberRefused.status)
				endSyncRequest(ownerId, ownerToken, ownerSync)

				val memberSync1 = projectSynchronizationBegan(memberId, memberToken, emptyState)
				val memberSync2 = projectSynchronizationBegan(memberId, memberToken, emptyState)
				val stale = downloadEntityRequest(memberId, memberToken, memberSync1.syncId, 1, null)
				assertFalse(stale.status.isSuccess())

				val ownerRefused = projectSynchronizationBeganRequest(ownerId, ownerToken, emptyState)
				assertEquals(HttpStatusCode.BadRequest, ownerRefused.status)
				endSyncRequest(memberId, memberToken, memberSync2)
			}
		}

	@Test
	fun `a member cannot begin a project sync while the owner's account sync is live`(): Unit = runBlocking {
		setUpProject()

		client().apply {
			beginAccountSync(ownerId, ownerToken)

			val refused = projectSynchronizationBeganRequest(memberId, memberToken, emptyState)
			assertEquals(HttpStatusCode.Conflict, refused.status)
			assertEquals(ApiErrorCode.PROJECT_SYNC_IN_PROGRESS, refused.body<HttpResponseError>().errorCode)
		}
	}

	@Test
	fun `a member mid-sync is not stopped by the owner's account sync`(): Unit = runBlocking {
		setUpProject()

		client().apply {
			val memberSync = projectSynchronizationBegan(memberId, memberToken, emptyState)
			beginAccountSync(ownerId, ownerToken)

			val downloaded = downloadEntityRequest(memberId, memberToken, memberSync.syncId, 1, null)
			assertEquals(HttpStatusCode.OK, downloaded.status)
			endSyncRequest(memberId, memberToken, memberSync)
		}
	}

	@Test
	fun `the owner's delete, rename and move are refused while a member holds a project session`(): Unit =
		runBlocking {
			setUpProject()

			client().apply {
				val memberSync = projectSynchronizationBegan(memberId, memberToken, emptyState)
				val accountSyncId = beginAccountSync(ownerId, ownerToken)

				val responses = listOf(
					accountRequest("delete", ownerId, ownerToken, accountSyncId),
					accountRequest("rename", ownerId, ownerToken, accountSyncId, "projectName" to "Renamed"),
					accountRequest("move", ownerId, ownerToken, accountSyncId),
				)
				responses.forEach { response ->
					assertEquals(HttpStatusCode.Conflict, response.status)
					assertEquals(ApiErrorCode.PROJECT_SYNC_IN_PROGRESS, response.body<HttpResponseError>().errorCode)
				}
				assertNotNull(
					database().serverDatabase.projectQueries
						.getProject(ownerId, TestDataSet1.project1.uuid.toString()).executeAsOneOrNull()
				)

				endSyncRequest(memberId, memberToken, memberSync)
				val renamed = accountRequest("rename", ownerId, ownerToken, accountSyncId, "projectName" to "Renamed")
				assertEquals(HttpStatusCode.OK, renamed.status)
			}
		}

	@Test
	fun `the owner's own project session on another install does not block their rename`(): Unit = runBlocking {
		setUpProject()
		val otherInstall =
			createAuthToken(ownerId, "owner-install-2", database = database(), tokenHasher = tokenHasher())

		client().apply {
			projectSynchronizationBegan(ownerId, ownerToken, emptyState)
			val accountSyncId = beginAccountSync(ownerId, otherInstall)

			val renamed = accountRequest("rename", ownerId, otherInstall, accountSyncId, "projectName" to "Renamed")
			assertEquals(HttpStatusCode.OK, renamed.status)
		}
	}
}
