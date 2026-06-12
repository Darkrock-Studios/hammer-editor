package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Proves the sync mechanics the editorial-review feature relies on: the server
 * creating entities on its own (minting a draft at `last_id + 1` and bumping
 * `last_id`) and rewriting a scene's content, with no client-side changes.
 *
 * Expected behavior, all via existing machinery:
 *  - a clean client downloads the minted draft on its next sync,
 *  - a client that claimed the same ID offline re-IDs its own entity above the
 *    mint ([IdConflictResolutionOperation]) and both entities survive,
 *  - a server-rewritten scene auto-downloads to a clean client,
 *  - a client with unsynced local edits gets the standard conflict, with the
 *    rewritten text as the server version.
 */
class ServerOriginatedEntitiesTest : RoundTripTestBase() {

	private val draftCreated = Instant.fromEpochSeconds(1_750_000_000)

	private fun mintedDraft(id: Int, sceneId: Int, content: String) =
		ApiProjectEntity.SceneDraftEntity(
			id = id,
			sceneId = sceneId,
			created = draftCreated,
			// Must satisfy SceneDraftsDatasource.validDraftName (alphanumeric, space, apostrophe)
			name = "Editorial Review",
			content = content,
		)

	private fun serverLastId(projectName: String): Int {
		val lastId = database().serverDatabase.projectQueries
			.findProjectByName(userId, projectName)
			.executeAsOneOrNull()
			?.last_id
		assertNotNull(lastId, "Project '$projectName' should exist on the server")
		return lastId.toInt()
	}

	private fun bumpServerLastId(serverNumericProjectId: Long, newLastId: Int) {
		database().execute(
			"UPDATE project SET last_id = $newLastId WHERE id = $serverNumericProjectId;"
		)
	}

	private fun sceneRewrite(scene: SceneItem, newContent: String) =
		ApiProjectEntity.SceneEntity(
			id = scene.id,
			sceneType = ApiSceneType.Scene,
			order = scene.order,
			name = scene.name,
			path = listOf(0),
			content = newContent,
		)

	private suspend fun createSyncedScene(
		client: HeadlessClient,
		name: String,
		content: String,
	): SceneItem {
		val scene = client.sceneEditor.createScene(parent = null, sceneName = name)
		assertNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = content)
		)
		assertTrue(client.syncNoConflict(), "Initial sync should succeed")
		return scene
	}

	@Test
	@Timeout(value = 60)
	fun `clean client downloads server-minted draft`() = runBlocking {
		val projectName = "mint draft"
		val client = newClient(projectName)
		val scene = createSyncedScene(client, "Reviewed Scene", "Original content")

		val numericProjectId = serverNumericProjectIdFor(projectName)
		assertNotNull(numericProjectId)
		val mintedId = serverLastId(projectName) + 1
		seedServerEntity(numericProjectId, mintedDraft(mintedId, scene.id, "Original content"))
		bumpServerLastId(numericProjectId, mintedId)

		assertTrue(client.syncNoConflict(), "Post-mint sync should succeed")

		val draftDef = client.draftRepository.getDraftDef(mintedId)
		assertNotNull(draftDef, "Minted draft should have downloaded to the client")
		assertEquals(scene.id, draftDef.sceneId, "Draft should reference the reviewed scene")
		assertEquals("Editorial Review", draftDef.draftName)
		assertEquals(draftCreated, draftDef.draftTimestamp)
		assertEquals(
			"Original content",
			client.draftRepository.loadDraftContent(draftDef),
			"Draft content should round-trip",
		)

		assertEquals(
			mintedId,
			serverLastId(projectName),
			"end_sync must not regress last_id below the minted ID",
		)
	}

	@Test
	@Timeout(value = 60)
	fun `offline ID collision re-IDs client entity and preserves minted draft`() = runBlocking<Unit> {
		val projectName = "mint collision"
		val client = newClient(projectName)
		val scene = createSyncedScene(client, "First Scene", "First content")

		val numericProjectId = serverNumericProjectIdFor(projectName)
		assertNotNull(numericProjectId)
		val mintedId = serverLastId(projectName) + 1

		// Offline: the client claims the same ID the server is about to mint.
		val offlineScene = client.sceneEditor.createScene(parent = null, sceneName = "Offline Scene")
		assertNotNull(offlineScene)
		assertEquals(
			mintedId, offlineScene.id,
			"Precondition: offline scene must claim the ID the server will mint",
		)
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = offlineScene, markdown = "Offline content")
		)

		seedServerEntity(numericProjectId, mintedDraft(mintedId, scene.id, "First content"))
		bumpServerLastId(numericProjectId, mintedId)

		assertTrue(client.syncNoConflict(), "Collision sync should resolve without a conflict")

		val reIded = client.sceneEditor.getScenes().firstOrNull { it.name == "Offline Scene" }
		assertNotNull(reIded, "Offline scene should still exist on the client")
		assertEquals(mintedId + 1, reIded.id, "Offline scene should be re-IDed above the mint")
		assertEquals("Offline content", client.sceneEditor.loadSceneMarkdownRaw(reIded))

		val draftDef = client.draftRepository.getDraftDef(mintedId)
		assertNotNull(draftDef, "Minted draft should survive at its original ID")
		assertEquals("First content", client.draftRepository.loadDraftContent(draftDef))

		assertNotNull(
			serverEntityHash(projectName, mintedId + 1),
			"Server should have received the re-IDed offline scene",
		)
	}

	@Test
	@Timeout(value = 60)
	fun `review commit lands on clean client without conflict`() = runBlocking {
		val projectName = "review commit clean"
		val client = newClient(projectName)
		val scene = createSyncedScene(client, "Reviewed Scene", "Original content")

		val numericProjectId = serverNumericProjectIdFor(projectName)
		assertNotNull(numericProjectId)
		val mintedId = serverLastId(projectName) + 1
		val revised = "Revised content after editorial review"

		// Simulate the commit: reviewed draft minted + working scene rewritten.
		seedServerEntity(numericProjectId, mintedDraft(mintedId, scene.id, revised))
		mutateServerEntity(numericProjectId, sceneRewrite(scene, revised))
		bumpServerLastId(numericProjectId, mintedId)

		assertTrue(client.syncNoConflict(), "Clean client must absorb the commit without conflict")

		val refreshed = client.sceneEditor.getSceneItemFromId(scene.id)
		assertNotNull(refreshed)
		assertEquals(
			revised,
			client.sceneEditor.loadSceneMarkdownRaw(refreshed),
			"Scene content should have been replaced by the reviewed text",
		)

		val draftDef = client.draftRepository.getDraftDef(mintedId)
		assertNotNull(draftDef, "Reviewed draft should have downloaded alongside the rewrite")
		assertEquals(revised, client.draftRepository.loadDraftContent(draftDef))
	}

	@Test
	@Timeout(value = 60)
	fun `review commit conflicts with dirty client but draft still arrives`() = runBlocking {
		val projectName = "review commit dirty"
		val client = newClient(projectName)
		val scene = createSyncedScene(client, "Reviewed Scene", "Original content")

		val numericProjectId = serverNumericProjectIdFor(projectName)
		assertNotNull(numericProjectId)
		val mintedId = serverLastId(projectName) + 1
		val revised = "Revised content after editorial review"

		seedServerEntity(numericProjectId, mintedDraft(mintedId, scene.id, revised))
		mutateServerEntity(numericProjectId, sceneRewrite(scene, revised))
		bumpServerLastId(numericProjectId, mintedId)

		// Unsynced local edit to the same scene.
		client.sceneEditor.storeSceneMarkdownRaw(
			sceneItem = SceneContent(scene = scene, markdown = "Locally diverged content")
		)

		var conflictSeen: ApiProjectEntity? = null
		val ok = client.sync(resolveConflict = { serverEntity ->
			conflictSeen = serverEntity
			serverEntity
		})
		assertTrue(ok, "Sync should succeed after conflict resolution")

		val serverScene = conflictSeen as? ApiProjectEntity.SceneEntity
		assertNotNull(serverScene, "Conflict should surface for the rewritten scene")
		assertEquals(
			revised,
			serverScene.content,
			"Server side of the conflict should be the reviewed text",
		)

		val refreshed = client.sceneEditor.getSceneItemFromId(scene.id)
		assertNotNull(refreshed)
		assertEquals(
			revised,
			client.sceneEditor.loadSceneMarkdownRaw(refreshed),
			"Resolving to server should adopt the reviewed text",
		)

		val draftDef = client.draftRepository.getDraftDef(mintedId)
		assertNotNull(draftDef, "Reviewed draft should download despite the scene conflict")
		assertEquals(revised, client.draftRepository.loadDraftContent(draftDef))
	}
}
