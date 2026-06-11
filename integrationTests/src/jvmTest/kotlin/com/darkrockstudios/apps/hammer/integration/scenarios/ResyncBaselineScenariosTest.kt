package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Single-client resync must never raise a conflict, whichever field changed or however the baseline
 * was established: metadata-only edits, renames, and an edit after a download sets the baseline.
 * Each asserts the server's stored hash actually changed, so a resync that uploaded nothing can't
 * pass vacuously.
 */
class ResyncBaselineScenariosTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `editing only scene metadata and resyncing does not conflict`() = runBlocking {
		val project = "metadata resync"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "body"))
		assertTrue(client.sync(), "first sync should succeed")
		val before = serverEntityHash(project, scene.id)
		assertNotNull(before, "scene should be on the server after the first sync")

		// Change metadata only (no content edit, so lastEdited is not bumped).
		val metadata = client.sceneEditorService.loadSceneMetadata(scene.id)
		client.sceneEditorService.storeMetadata(
			metadata.copy(notes = "a note added later", outline = "an outline"),
			scene.id,
		)

		assertTrue(client.syncNoConflict(), "second sync should succeed")
		assertNotEquals(
			before, serverEntityHash(project, scene.id),
			"the metadata edit must have reached the server (else the resync proved nothing)",
		)
	}

	@Test
	@Timeout(value = 60)
	fun `renaming a scene and resyncing does not conflict`() = runBlocking {
		val project = "rename resync"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Original Name")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "body"))
		assertTrue(client.sync(), "first sync should succeed")
		val before = serverEntityHash(project, scene.id)
		assertNotNull(before, "scene should be on the server after the first sync")

		client.sceneEditorService.renameScene(scene, "Renamed")

		assertTrue(client.syncNoConflict(), "second sync should succeed")
		assertNotEquals(
			before, serverEntityHash(project, scene.id),
			"the rename must have reached the server",
		)
	}

	@Test
	@Timeout(value = 60)
	fun `editing after downloading a server change and resyncing does not conflict`() = runBlocking {
		val project = "download then edit"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Shared")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "v1"))
		assertTrue(client.sync(), "first sync should succeed")

		// Another device edits the same scene; the client has no local change, so the next sync
		// downloads the server's version and the baseline is established from that download.
		mutateServerEntity(
			requireNotNull(serverNumericProjectIdFor(project)),
			E2eTestData.createTestScene(id = scene.id).copy(content = "server version"),
		)
		assertTrue(client.sync(), "download sync should succeed")
		assertEquals(
			"server version",
			client.sceneEditor.loadSceneMarkdownRaw(requireNotNull(client.sceneEditor.getSceneItemFromId(scene.id))),
			"client should have the server's content after download",
		)
		val afterDownload = serverEntityHash(project, scene.id)

		// Edit the downloaded scene and resync — must not conflict, and must land.
		client.sceneEditor.storeSceneMarkdownRaw(
			SceneContent(requireNotNull(client.sceneEditor.getSceneItemFromId(scene.id)), "local follow-up edit"),
		)
		assertTrue(client.syncNoConflict(), "resync after local edit should succeed")
		assertNotEquals(
			afterDownload, serverEntityHash(project, scene.id),
			"the follow-up edit must have reached the server",
		)
	}
}
