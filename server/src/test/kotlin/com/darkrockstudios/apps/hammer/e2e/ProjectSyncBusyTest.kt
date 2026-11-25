package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * A project sync refused because something else holds the project says it's busy, with
 * Retry-After, so the client waits and tries again instead of failing the sync.
 */
class ProjectSyncBusyTest : ProjectSyncTestBase() {

	private val userId = 1L
	private val emptyState = ClientEntityState(entities = emptySet())

	private suspend fun setUp(vararg installs: String): List<Token> {
		val database = database()
		createTestServer(SERVER_EMPTY, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val tokens = installs.map { createAuthToken(userId, it, database = database, tokenHasher = tokenHasher()) }
		doStartServer()
		return tokens
	}

	private suspend fun assertBusy(response: HttpResponse) {
		assertEquals(HttpStatusCode.Conflict, response.status)
		assertEquals(ApiErrorCode.PROJECT_SYNC_IN_PROGRESS, response.body<HttpResponseError>().errorCode)
		assertEquals("5", response.headers[HttpHeaders.RetryAfter])
	}

	@Test
	fun `a second device is told the project is busy, then syncs once the first finishes`(): Unit = runBlocking {
		val (laptop, desktop) = setUp("install-laptop", "install-desktop")

		client().apply {
			val first = projectSynchronizationBegan(userId, laptop, emptyState)

			assertBusy(projectSynchronizationBeganRequest(userId, desktop, emptyState))

			endSyncRequest(userId, laptop, first)
			val second = projectSynchronizationBegan(userId, desktop, emptyState)
			endSyncRequest(userId, desktop, second)
		}
	}

	@Test
	fun `a project sync waits on the account's own account sync`(): Unit = runBlocking {
		val (laptop) = setUp("install-laptop")

		client().apply {
			val accountSync = post(api("projects/$userId/begin_sync")) {
				headers {
					append(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
					append("Authorization", "Bearer ${laptop.auth}")
				}
			}
			assertEquals(HttpStatusCode.OK, accountSync.status)

			assertBusy(projectSynchronizationBeganRequest(userId, laptop, emptyState))
		}
	}
}
