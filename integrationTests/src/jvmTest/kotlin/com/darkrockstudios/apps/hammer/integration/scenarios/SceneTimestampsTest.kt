package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SceneTimestampsTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `client upload carries created and lastEdited to the server`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "timestamps upload",
			serverSettings = makeServerSettings(),
		)

		val beforeEdit = kotlin.time.Clock.System.now()
		val scene = client.sceneEditorService.createScene(parent = null, sceneName = "Timed Scene")
		assertNotNull(scene)
		// Persist synchronously: onContentChanged registers the editor buffer asynchronously, so an
		// immediate storeSceneBuffer would find none and silently no-op.
		assertTrue(
			client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "first content")),
			"raw store should persist the scene content to disk",
		)

		assertTrue(client.sync(), "Sync should succeed")
		val afterSync = kotlin.time.Clock.System.now()

		val sceneMetadataDatasource: SceneMetadataDatasource = client.scope.get()
		val localMetadata = sceneMetadataDatasource.loadMetadata(scene.id)
		assertNotNull(localMetadata, "Local SceneMetadata should exist after edit")
		assertNotNull(localMetadata.created, "createScene + edit must populate created locally")
		assertNotNull(localMetadata.lastEdited, "edit must populate lastEdited locally")

		val numericProjectId = serverNumericProjectIdFor("timestamps upload")
		assertNotNull(numericProjectId)
		val storedEntity = loadServerSceneEntity(numericProjectId, scene.id)

		assertEquals(
			"first content", storedEntity.content,
			"the uploaded scene content must reach the server",
		)

		// A client stamp of NOW is observationally identical to a server stamp of NOW on receipt,
		// so assert the timestamps land inside the test window rather than pinning an exact value —
		// ruling out drops, epoch-0 resets, and clearly-wrong stamps.
		val serverCreated = storedEntity.created
		val serverLastEdited = storedEntity.lastEdited
		assertNotNull(serverCreated, "server stored a created timestamp")
		assertNotNull(serverLastEdited, "server stored a lastEdited timestamp")
		assertTrue(
			serverCreated in beforeEdit..afterSync,
			"created should land in [beforeEdit, afterSync] but was $serverCreated",
		)
		assertTrue(
			serverLastEdited in beforeEdit..afterSync,
			"lastEdited should land in [beforeEdit, afterSync] but was $serverLastEdited",
		)

		client.close()
	}

	@Test
	@Timeout(value = 60)
	fun `client download writes server's created and lastEdited into local metadata`() = runBlocking {
		val client = HeadlessClient.create(
			projectName = "timestamps download",
			serverSettings = makeServerSettings(),
		)

		assertTrue(client.sync(), "First sync should succeed")

		val numericProjectId = serverNumericProjectIdFor("timestamps download")
		assertNotNull(numericProjectId)

		val serverCreated = Instant.fromEpochSeconds(1_700_000_000)
		val serverLastEdited = Instant.fromEpochSeconds(1_700_086_400) // +1 day
		val scene = E2eTestData.createTestScene(id = 1).copy(
			created = serverCreated,
			lastEdited = serverLastEdited,
		)
		seedServerEntity(numericProjectId, scene)
		database().execute("UPDATE project SET last_id = 1 WHERE id = $numericProjectId;")

		assertTrue(client.sync(), "Second sync should succeed")

		val sceneMetadataDatasource: SceneMetadataDatasource = client.scope.get()
		val localMetadata = sceneMetadataDatasource.loadMetadata(1)
		assertNotNull(localMetadata, "Downloaded scene should produce a local SceneMetadata file")

		assertEquals(serverCreated, localMetadata.created, "server's created must land in local metadata")
		assertEquals(
			serverLastEdited, localMetadata.lastEdited,
			"server's lastEdited must land in local metadata — this is the continuous-client signal",
		)

		client.close()
	}

	private fun loadServerSceneEntity(
		serverNumericProjectId: Long,
		entityId: Int,
	): ApiProjectEntity.SceneEntity = runBlocking {
		val row = database().serverDatabase.storyEntityQueries
			.getEntity(userId = userId, projectId = serverNumericProjectId, id = entityId.toLong())
			.executeAsOne()
		val account = database().serverDatabase.accountQueries.getAccount(userId).executeAsOne()
		val decrypted = encryptor().decrypt(row.content, account.cipher_secret)
		Json.decodeFromString(ApiProjectEntity.SceneEntity.serializer(), decrypted)
	}
}
