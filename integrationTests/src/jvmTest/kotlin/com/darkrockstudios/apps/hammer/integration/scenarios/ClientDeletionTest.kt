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
 * Scenario 6: client deletes a scene that exists on both sides. After sync the
 * server should drop the entity and record a deletion tombstone so other devices
 * learn about the delete.
 */
class ClientDeletionTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `client deletion propagates to server`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "delete project",
			serverSettings = makeServerSettings(),
		)

		// Set up: create a scene and sync it so both sides have it.
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Doomed Scene")
		assertNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "About to be removed")
		)
		assertTrue(client.sync(), "Initial sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("delete project")
		assertNotNull(numericProjectId)

		// Sanity: server has the entity.
		val beforeDelete = database().serverDatabase.storyEntityQueries
			.getEntityDefs(userId = userId, projectId = numericProjectId)
			.executeAsList()
		assertEquals(1, beforeDelete.size, "Server should have the entity before delete")

		// Client deletes the scene locally, then syncs.
		val deleted = client.sceneEditor.deleteScene(scene)
		assertTrue(deleted, "Local delete should succeed")
		assertTrue(client.sync(), "Sync after delete should succeed")

		// Server's story_entity row should now be gone.
		val afterDelete = database().serverDatabase.storyEntityQueries
			.getEntityDefs(userId = userId, projectId = numericProjectId)
			.executeAsList()
		assertEquals(0, afterDelete.size, "Server should have removed the entity row")

		// Server should also record a deletion tombstone for other devices.
		val tombstone = database().serverDatabase.deletedEntityQueries
			.checkIsDeleted(userId = userId, projectId = numericProjectId, entityId = scene.id.toLong())
			.executeAsOne()
		assertTrue(tombstone, "Server should mark entity as deleted in the tombstone table")

		client.close()
	}
}
