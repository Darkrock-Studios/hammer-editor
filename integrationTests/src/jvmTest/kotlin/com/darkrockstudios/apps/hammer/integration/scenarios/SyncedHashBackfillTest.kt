package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration guard. After an upgrade the syncedHashes map is empty (the field is new), so a dirty
 * entity would upload with a null baseline and the server would skip the conflict check, silently
 * overwriting a concurrent edit. The first sync must backfill baselines for in-sync entities so a
 * later edit still catches a genuine conflict.
 */
class SyncedHashBackfillTest : RoundTripTestBase() {

	@Test
	@Timeout(value = 60)
	fun `post-upgrade sync backfills syncedHashes for an in-sync entity`() = runBlocking {
		val project = "backfill in-sync"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "body"))
		assertTrue(client.sync(), "initial sync should succeed")

		val journal: SyncJournal = client.scope.get()
		// Simulate an upgrade: empty the syncedHashes map.
		journal.saveSyncData(journal.loadSyncData().copy(syncedHashes = emptyMap()))
		assertNull(journal.loadSyncData().syncedHashes[scene.id], "precondition: syncedHashes wiped")

		// A plain sync with no local edits must restore the baseline.
		assertTrue(client.sync(), "post-upgrade sync should succeed")

		assertEquals(
			serverEntityHash(project, scene.id),
			journal.loadSyncData().syncedHashes[scene.id],
			"the in-sync entity's baseline must be backfilled to the server's hash",
		)
	}

	@Test
	@Timeout(value = 60)
	fun `after backfill a concurrent server edit is caught as a conflict`() = runBlocking {
		val project = "backfill conflict"
		val client = newClient(project)
		val scene = client.sceneEditor.createScene(parent = null, sceneName = "Scene")
		requireNotNull(scene)
		client.sceneEditor.storeSceneMarkdownRaw(SceneContent(scene, "v1"))
		assertTrue(client.sync(), "initial sync should succeed")

		val journal: SyncJournal = client.scope.get()
		journal.saveSyncData(journal.loadSyncData().copy(syncedHashes = emptyMap()))

		// Post-upgrade sync restores the baseline for the untouched entity.
		assertTrue(client.sync(), "post-upgrade sync should succeed")

		// Another device changes the same scene on the server.
		mutateServerEntity(
			requireNotNull(serverNumericProjectIdFor(project)),
			com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createTestScene(id = scene.id)
				.copy(content = "server version"),
		)

		// The client edits it too and syncs; the divergence from the server must surface as a conflict.
		client.sceneEditor.storeSceneMarkdownRaw(
			SceneContent(requireNotNull(client.sceneEditor.getSceneItemFromId(scene.id)), "local version"),
		)
		var conflicted = false
		assertTrue(
			client.sync(resolveConflict = { entity -> conflicted = true; entity }),
			"sync should complete",
		)
		assertTrue(conflicted, "the concurrent server edit must surface as a conflict, not a silent overwrite")
	}
}
