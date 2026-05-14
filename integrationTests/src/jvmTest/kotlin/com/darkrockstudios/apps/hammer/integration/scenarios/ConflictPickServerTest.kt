package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scenario 4: client and server have diverged on the same entity. The conflict
 * resolver picks the server's version — the client's local edit is discarded
 * and the server's content lands on the client filesystem.
 */
class ConflictPickServerTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `conflict resolved to server overwrites client edit`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "conflict server",
			serverSettings = makeServerSettings(),
		)

		// 1. Both sides start in sync with a single scene.
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Shared Scene")
		assertNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "Original shared content")
		)
		assertTrue(client.sync(), "Initial sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("conflict server")
		assertNotNull(numericProjectId)

		// 2. Server-side: simulate another device updating the same scene.
		val serverVersion = E2eTestData.createTestScene(id = scene.id).copy(
			name = "Server's Title",
			content = "Server's version of the content",
		)
		mutateServerEntity(numericProjectId, serverVersion)

		// 3. Client-side: edit the same scene locally.
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "Client's diverged content")
		)

		// 4. Sync — server returns 409 with its version; resolver picks server.
		var conflictSeen: ApiProjectEntity? = null
		val ok = client.sync(resolveConflict = { serverEntity ->
			conflictSeen = serverEntity
			serverEntity
		})
		assertTrue(ok, "Sync should succeed after conflict resolution")
		assertNotNull(conflictSeen, "Resolver should have been invoked")

		// 5. Re-fetch via id — the scene's filename may have changed after the
		// server rename, so we can't reuse the original SceneItem reference.
		val refreshed = client.sceneEditor.getSceneItemFromId(scene.id)
		assertNotNull(refreshed, "Scene should still exist on client by id")
		val onDisk = client.sceneEditor.loadSceneMarkdownRaw(refreshed)
		assertEquals(
			"Server's version of the content",
			onDisk,
			"Client should have adopted server's content after conflict",
		)

		client.close()
	}
}
