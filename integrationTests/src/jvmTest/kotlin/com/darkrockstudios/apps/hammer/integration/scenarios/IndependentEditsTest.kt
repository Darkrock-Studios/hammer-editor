package com.darkrockstudios.apps.hammer.integration.scenarios

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
 * Scenario 3: server has entity A (a scene), client has entity B (a different
 * scene). The two edits don't conflict — after sync both sides should have both
 * entities.
 */
class IndependentEditsTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `independent edits on both sides merge without conflict`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "indep project",
			serverSettings = makeServerSettings(),
		)

		// First sync — registers project on server.
		assertTrue(client.sync(), "First sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("indep project")
		assertNotNull(numericProjectId)

		// Server gains scene id=1 (simulating another device).
		seedServerEntity(numericProjectId, E2eTestData.createTestScene(id = 1))
		database().execute("UPDATE project SET last_id = 1 WHERE id = $numericProjectId;")

		// Client creates a different scene locally with id=2 — IdRepository would
		// claim id=1 (it doesn't know about the server's reservation), causing an
		// ID conflict. Force id=2 instead.
		val localScene = client.sceneEditor.createScene(parent = null, sceneName = "Local Scene", forceId = 2)
		assertNotNull(localScene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = localScene, markdown = "Locally authored content")
		)

		assertTrue(client.sync(), "Second sync should succeed")

		// Client should now have both scenes.
		val clientScenes = client.sceneEditor.getScenes().map { it.id }.sorted()
		assertEquals(listOf(1, 2), clientScenes, "Client should have both scenes after sync")

		// Server should now have both scenes too.
		val serverEntities = database().serverDatabase.storyEntityQueries
			.getEntityDefs(userId = userId, projectId = numericProjectId)
			.executeAsList()
			.map { it.id }
			.sorted()
		assertEquals(listOf(1L, 2L), serverEntities, "Server should have both scenes after sync")

		client.close()
	}
}
