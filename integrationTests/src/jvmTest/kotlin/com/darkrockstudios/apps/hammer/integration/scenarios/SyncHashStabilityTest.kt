package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * After a clean sync, the hash the client computes for a scene (its conflict baseline) must equal
 * the hash the server stored on upload. Any divergence means the next sync conflicts even though
 * nothing changed — so this pins the client and server hashers to the same answer for the same
 * entity, end to end. Design-agnostic: holds whatever fields participate in the hash.
 */
class SyncHashStabilityTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `client sync hash matches the hash the server stored`() = runBlocking {
		val project = "hash stability agreement"
		val client = newClient(project)

		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Agreed Scene")
		assertNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "content that rides the wire"),
		)
		assertTrue(client.sync(), "Sync should succeed")

		val serverHash = serverEntityHash(project, scene.id)
		assertNotNull(serverHash, "Server should have stored a hash for the synced scene")

		val synchronizer: ClientSceneSynchronizer = client.scope.get()
		val clientHash = synchronizer.getEntityHash(scene.id)
		assertNotNull(clientHash, "Client should compute a hash for the synced scene")

		assertEquals(
			serverHash, clientHash,
			"Client's sync hash differs from the hash the server stored for the same scene — " +
				"every resync of this untouched scene would raise a phantom conflict.",
		)
	}
}
