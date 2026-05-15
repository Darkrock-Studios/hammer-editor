package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scenario 2: client creates a scene locally; server is empty. After sync the
 * server's DB should hold the entity with a matching hash.
 */
class ClientUploadsEntityTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `client-created scene is uploaded to server on sync`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "upload project",
			serverSettings = makeServerSettings(),
		)

		val scene = client.sceneEditor.createScene(parent = null, sceneName = "First Scene")
		assertNotNull(scene, "Scene creation should succeed")
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "Once upon a time, in a land far away."),
		)

		val ok = client.sync()
		assertTrue(ok, "Sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("upload project")
		assertNotNull(numericProjectId, "Server should have created the project")

		val serverEntities = database().serverDatabase.storyEntityQueries
			.getEntityDefs(userId = userId, projectId = numericProjectId)
			.executeAsList()
		assertEquals(1, serverEntities.size, "Server should have exactly one entity")
		assertEquals(scene.id.toLong(), serverEntities.single().id)

		client.close()
	}
}
