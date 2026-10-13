package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A backup carries the sync journal from when it was taken, so after a restore the journal
 * describes the server as it was then. The first sync after a restore must conflict-check the
 * restored content against the server: neither the server's newer copy nor the restored one may
 * silently win.
 */
class RestoreBackupSyncTest : RoundTripTestBase() {

	private suspend fun restore(client: HeadlessClient, backup: ProjectBackupDef): HeadlessClient {
		client.close()
		assertTrue(get<ProjectBackupRepository>().restoreBackup(backup, client.projectDef.path))
		return reopen(client)
	}

	private suspend fun HeadlessClient.writeScene(id: Int, markdown: String) {
		sceneEditor.storeSceneMarkdownRaw(SceneContent(requireNotNull(sceneEditor.getSceneItemFromId(id)), markdown))
	}

	private suspend fun HeadlessClient.syncRecordingConflict(): Boolean {
		var conflicted = false
		assertTrue(sync(resolveConflict = { entity -> conflicted = true; entity }), "sync should complete")
		return conflicted
	}

	@Test
	@Timeout(value = 60)
	fun `a scene the server moved on from conflicts instead of reverting the restore`() = runBlocking {
		val project = "restore server newer"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())
		val backup = assertNotNull(get<ProjectBackupRepository>().createBackup(client.projectDef))

		// Work after the backup reaches the server.
		client.writeScene(scene.id, "after the backup")
		assertTrue(client.sync())

		val restored = restore(client, backup)
		assertTrue(restored.syncRecordingConflict(), "the server's newer copy must not silently replace the restore")
	}

	@Test
	@Timeout(value = 60)
	fun `a restored edit conflicts with a newer server copy instead of overwriting it`() = runBlocking {
		val project = "restore local edit"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "v1")
		assertTrue(client.sync())

		client.writeScene(scene.id, "unsynced edit in the backup")
		val backup = assertNotNull(get<ProjectBackupRepository>().createBackup(client.projectDef))

		mutateServerEntity(
			requireNotNull(serverNumericProjectIdFor(project)),
			E2eTestData.createTestScene(id = scene.id).copy(content = "another device's version"),
		)

		val restored = restore(client, backup)
		assertTrue(restored.syncRecordingConflict(), "the restored edit must not silently overwrite the server")
	}

	@Test
	@Timeout(value = 60)
	fun `a restore the server never moved past resyncs without a conflict`() = runBlocking {
		val project = "restore unchanged"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())
		val backup = assertNotNull(get<ProjectBackupRepository>().createBackup(client.projectDef))

		val restored = restore(client, backup)
		assertTrue(restored.syncNoConflict())
		assertEquals(
			"backed up",
			restored.sceneEditor.loadSceneMarkdownRaw(requireNotNull(restored.sceneEditor.getSceneItemFromId(scene.id))),
		)
		assertConverged(project, restored)
	}
}
