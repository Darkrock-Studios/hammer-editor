package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupRestoreResult
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.InitialSyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectNeedsAccountSyncException
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
		assertEquals(BackupRestoreResult.Restored, get<ProjectBackupService>().restoreBackup(backup, client.projectDef.path))
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

	/** Loses the project's `project.toml` and heals the folder at startup, under an id the server never saw. */
	private suspend fun healUnderFreshId(client: HeadlessClient) {
		client.close()
		get<FileSystem>().delete(client.projectPath / ProjectMetadata.FILENAME)
		get<ProjectsRepository>().healUnmarkedProjectDirectories()
	}

	/** Account sync adopts the same-named server project's id for the closed [client], which then reopens. */
	private suspend fun adoptByAccountSync(client: HeadlessClient, project: String): HeadlessClient {
		assertNotEquals(serverProjectId(project), localId(client), "the local id should be one the server never saw")
		assertTrue(accountSync(), "account sync should succeed")
		assertEquals(serverProjectId(project), localId(client), "account sync should adopt the server project's id")
		return reopen(client)
	}

	private fun localId(client: HeadlessClient): ProjectId =
		get<ProjectMetadataDatasource>().loadProjectId(client.projectDef)

	private fun serverProjectId(name: String): ProjectId = ProjectId(
		requireNotNull(database().serverDatabase.projectQueries.findProjectByName(userId, name).executeAsOneOrNull()).uuid
	)

	private fun serverProjects(): Map<String, String> =
		database().serverDatabase.projectQueries.getProjects(userId).executeAsList().associate { it.name to it.uuid }

	/** Runs a project sync that must fail, returning why. */
	private suspend fun HeadlessClient.projectSyncFailure(): Throwable? {
		val result = synchronizer.execute(
			InitialSyncOperationState(onlyNew = false),
			onProgress = { _, _ -> },
			onLog = { },
			onConflict = { error("unexpected conflict") },
			onComplete = { },
		)
		return assertIs<ClientResult.Failure<*>>(result, "project sync should fail").exception
	}

	@Test
	@Timeout(value = 60)
	fun `a scene the server moved on from conflicts instead of reverting the restore`() = runBlocking {
		val project = "restore server newer"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())
		val backup = assertNotNull(get<ProjectBackupService>().createBackup(client.projectDef))

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
		val backup = assertNotNull(get<ProjectBackupService>().createBackup(client.projectDef))

		mutateServerEntity(
			requireNotNull(serverNumericProjectIdFor(project)),
			E2eTestData.createTestScene(id = scene.id).copy(content = "another device's version"),
		)

		val restored = restore(client, backup)
		assertTrue(restored.syncRecordingConflict(), "the restored edit must not silently overwrite the server")
	}

	@Test
	@Timeout(value = 60)
	fun `a backup from before the first sync conflicts with the newer server copy`() = runBlocking {
		val project = "restore no journal"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())

		// Stand in for a backup taken before the project ever synced: it carries no journal.
		val fileSystem = get<FileSystem>()
		val journal = client.projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
		val aside = journal.parent!! / "sync.json.aside"
		fileSystem.atomicMove(journal, aside)
		val backup = assertNotNull(get<ProjectBackupService>().createBackup(client.projectDef))
		fileSystem.atomicMove(aside, journal)

		client.writeScene(scene.id, "after the backup")
		assertTrue(client.sync())

		val restored = restore(client, backup)
		assertTrue(restored.syncRecordingConflict(), "the server's copy must not silently replace the restore")
	}

	@Test
	@Timeout(value = 60)
	fun `a restore the server never moved past resyncs without a conflict`() = runBlocking {
		val project = "restore unchanged"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())
		val backup = assertNotNull(get<ProjectBackupService>().createBackup(client.projectDef))

		val restored = restore(client, backup)
		assertTrue(restored.syncNoConflict())
		assertEquals(
			"backed up",
			restored.sceneEditor.loadSceneMarkdownRaw(requireNotNull(restored.sceneEditor.getSceneItemFromId(scene.id))),
		)
		assertConverged(project, restored)
	}

	@Test
	@Timeout(value = 60)
	fun `a restore under a fresh id that adopts the same-named server project still conflicts`() = runBlocking {
		val project = "restore adopt by name"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "backed up")
		assertTrue(client.sync())
		val backup = assertNotNull(get<ProjectBackupService>().createBackup(client.projectDef))

		client.writeScene(scene.id, "after the backup")
		assertTrue(client.sync())

		// No live project at the target, so the restore mints a fresh id.
		client.close()
		get<FileSystem>().delete(client.projectPath / ProjectMetadata.FILENAME)
		assertEquals(BackupRestoreResult.Restored, get<ProjectBackupService>().restoreBackup(backup, client.projectDef.path))

		val restored = adoptByAccountSync(client, project)
		assertTrue(restored.syncRecordingConflict(), "the restore must not silently overwrite the server")
	}

	@Test
	@Timeout(value = 60)
	fun `a healed folder that adopts the same-named server project conflicts with its newer copy`() = runBlocking {
		val project = "heal adopt by name"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "v1")
		assertTrue(client.sync())

		client.writeScene(scene.id, "unsynced local edit")
		mutateServerEntity(
			requireNotNull(serverNumericProjectIdFor(project)),
			E2eTestData.createTestScene(id = scene.id).copy(content = "another device's version"),
		)

		healUnderFreshId(client)
		val healed = adoptByAccountSync(client, project)
		assertTrue(healed.syncRecordingConflict(), "the healed copy must not silently overwrite the server")
	}

	@Test
	@Timeout(value = 60)
	fun `a healed folder's pending edit uploads to the same-named server project without a conflict`() = runBlocking {
		val project = "heal adopt pending edit"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "v1")
		assertTrue(client.sync())
		client.writeScene(scene.id, "unsynced local edit")

		healUnderFreshId(client)
		val healed = adoptByAccountSync(client, project)
		assertTrue(healed.syncNoConflict())
		assertEquals(
			"unsynced local edit",
			healed.sceneEditor.loadSceneMarkdownRaw(requireNotNull(healed.sceneEditor.getSceneItemFromId(scene.id))),
		)
		assertConverged(project, healed)
	}

	@Test
	@Timeout(value = 60)
	fun `project sync leaves a healed folder's id to account sync`() = runBlocking {
		val project = "heal needs account sync"
		val client = newClient(project)
		val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Scene"))
		client.writeScene(scene.id, "v1")
		assertTrue(client.sync())
		client.writeScene(scene.id, "unsynced local edit")

		healUnderFreshId(client)
		val healed = reopen(client)
		val healedId = localId(healed)
		val serverProjects = serverProjects()
		val serverHashes = serverEntityHashes(project)

		assertIs<ProjectNeedsAccountSyncException>(healed.projectSyncFailure())
		assertEquals(healedId, localId(healed))
		assertEquals(serverProjects, serverProjects())
		assertEquals(serverHashes, serverEntityHashes(project))

		healed.close()
		val adopted = adoptByAccountSync(healed, project)
		assertTrue(adopted.syncNoConflict())
		assertEquals(
			"unsynced local edit",
			adopted.sceneEditor.loadSceneMarkdownRaw(requireNotNull(adopted.sceneEditor.getSceneItemFromId(scene.id))),
		)
		assertConverged(project, adopted)
	}
}
