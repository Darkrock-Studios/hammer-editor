package com.darkrockstudios.apps.hammer.integration.scenarios

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
 * Scenario 1: server has a scene the client doesn't know about. After sync the
 * client filesystem should contain that scene.
 *
 * Pattern: first sync registers the project on the server; we then poke an
 * entity directly into the server DB tied to that project's numeric id, bump
 * the project's `last_id` so future client-side ID claims don't collide, then
 * sync a second time to trigger the download path.
 */
class ServerDownloadsEntityTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `client downloads server-only scene`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "download project",
			serverSettings = makeServerSettings(),
		)

		// First sync — registers the project on the server.
		assertTrue(client.sync(), "First sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("download project")
		assertNotNull(numericProjectId, "Server should have created the project")

		// Pre-seed a scene with id=1 on the server side.
		val scene = E2eTestData.createTestScene(id = 1)
		seedServerEntity(numericProjectId, scene)
		// Reserve id=1 on the project so the client doesn't claim it locally next.
		database().execute("UPDATE project SET last_id = 1 WHERE id = $numericProjectId;")

		// Second sync — client should download the new entity.
		assertTrue(client.sync(), "Second sync should succeed")

		val clientScenes = client.sceneEditor.getScenes()
		assertEquals(1, clientScenes.size, "Client should have exactly one scene after download")
		assertEquals(1, clientScenes.single().id, "Downloaded scene should have id=1")
		assertEquals("test scene 1", clientScenes.single().name, "Scene name should match server's")

		client.close()
	}
}
