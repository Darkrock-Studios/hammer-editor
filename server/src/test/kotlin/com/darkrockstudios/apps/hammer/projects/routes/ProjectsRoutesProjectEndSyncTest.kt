package com.darkrockstudios.apps.hammer.projects.routes

import com.darkrockstudios.apps.hammer.base.http.HEADER_SYNC_ID
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectsRoutesProjectEndSyncTest : ProjectsRoutesBaseTest() {

	@Test
	fun `Projects - End Sync - Success`() = testApplication {
		val syncId = "syncId-test"
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			projectsRepository.endProjectsSync(userId = userId, syncId = syncId)
		} returns SResult.success(Unit)

		defaultApplication()

		client.get("api/projects/0/end_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
			header(HEADER_SYNC_ID, syncId)
		}.apply {
			assertEquals(HttpStatusCode.OK, status)

			coVerify { projectsRepository.endProjectsSync(userId = userId, syncId = syncId) }
		}
	}

	@Test
	fun `Projects - End Sync - No SyncId`() = testApplication {
		val userId = 0L

		coEvery { accountsRepository.checkToken(userId, BEARER_TOKEN) } returns SResult.success(0L)
		coEvery { whiteListRepository.useWhiteList() } returns false

		defaultApplication()

		client.get("api/projects/0/end_sync") {
			header("Authorization", "Bearer $BEARER_TOKEN")
		}.apply {
			assertEquals(HttpStatusCode.BadRequest, status)

			coVerify(exactly = 0) {
				projectsRepository.endProjectsSync(
					userId = any(),
					syncId = any()
				)
			}
		}
	}
}