package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.datamigrator.getSerializerForType
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scenario 5: client and server have diverged on the same entity. The conflict
 * resolver picks the client's version, which gets force-uploaded to the server.
 */
class ConflictPickClientTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `conflict resolved to client force-uploads client edit`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "conflict client",
			serverSettings = makeServerSettings(),
		)

		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Shared Scene")
		assertNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "Original shared content")
		)
		assertTrue(client.sync(), "Initial sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("conflict client")
		assertNotNull(numericProjectId)

		// Server-side: simulate another device's update.
		mutateServerEntity(
			numericProjectId,
			E2eTestData.createTestScene(id = scene.id).copy(
				name = "Server's Title",
				content = "Server's version",
			)
		)

		// Client-side: edit the same scene locally with content we want to win.
		val clientFinal = "Client's diverged content"
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = clientFinal)
		)

		// Resolve by rebuilding the entity from the client's current on-disk
		// state — this is the "force-upload my version" path.
		val ok = client.sync(resolveConflict = { serverEntity ->
			val localContent = client.sceneEditor.loadSceneMarkdownRaw(scene)
			(serverEntity as ApiProjectEntity.SceneEntity).copy(
				name = serverEntity.name, // keep server's name; only content diverges
				content = localContent,
			)
		})
		assertTrue(ok, "Sync should succeed after conflict resolution")

		// Pull the server's stored entity back and confirm it matches client's edit.
		val serverRow = database().serverDatabase.storyEntityQueries
			.getEntity(userId = userId, projectId = numericProjectId, id = scene.id.toLong())
			.executeAsOne()
		val cipherSecret = database().serverDatabase.accountQueries
			.getAccount(userId).executeAsOne().cipher_secret
		val decryptedJson = encryptor().decrypt(serverRow.content, cipherSecret)
		val json = createJsonSerializer()
		val deserialized = json.decodeFromString(
			getSerializerForType(ApiProjectEntity.Type.SCENE),
			decryptedJson,
		) as ApiProjectEntity.SceneEntity
		assertEquals(clientFinal, deserialized.content, "Server should now hold the client's version")

		client.close()
	}
}
