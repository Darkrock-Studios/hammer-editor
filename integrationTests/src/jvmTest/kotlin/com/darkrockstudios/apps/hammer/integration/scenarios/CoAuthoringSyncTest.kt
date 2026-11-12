package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsService
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsService
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncToggle
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.integration.CoAuthoringTestBase
import com.darkrockstudios.apps.hammer.integration.HeadlessClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Co-authoring end to end: two accounts, each on its own install of the real client, against one
 * real server. Every assertion reads what a device holds on disk or what the server stores.
 */
class CoAuthoringSyncTest : CoAuthoringTestBase() {

	private val syncedOutcomes = setOf(ProjectSyncOutcome.Success, ProjectSyncOutcome.Unchanged)

	@Test
	@Timeout(value = 180)
	fun `a co-author's first sync brings the shared project down, marked with its owner`() = runBlocking {
		val shared = shareProject("first sync")

		on(coAuthorDevice) {
			val def = assertNotNull(localProject(shared.name))
			assertEquals(shared.id, def.id)
			assertEquals(coAuthorDevice.install.projectsRoot, def.path.toOkioPath().parent)
			val info = localInfo(def)
			assertEquals(owner.userId, info.ownerId)
			assertEquals(OWNER_PEN_NAME, info.ownerName)
			assertTrue(info.syncEnabled)
			assertEquals(ProjectOwner(owner.userId, OWNER_PEN_NAME), get<ProjectsRepository>().getOwner(def))
			assertTrue(get<ProjectsService>().offersLeave(def))

			val client = openClient(shared.name)
			shared.sceneIds.forEachIndexed { i, id -> assertEquals("owner wrote scene ${i + 1}", client.readScene(id)) }
			assertConverged(shared.name, client)
			assertResyncSilent(client)
		}
		assertEquals(emptyList(), database().serverDatabase.projectQueries.getProjects(coAuthor.userId).executeAsList())
	}

	@Test
	@Timeout(value = 180)
	fun `edits to different scenes by owner and co-author both arrive everywhere`() = runBlocking {
		val shared = shareProject("disjoint edits")
		val (a, b) = shared.sceneIds

		on(ownerDevice) {
			val client = openClient(shared.name)
			client.writeScene(a, "owner edited A")
			assertTrue(client.syncNoConflict())
		}
		val coAuthorHashes = on(coAuthorDevice) {
			val client = openClient(shared.name)
			client.writeScene(b, "co-author edited B")
			assertTrue(client.syncNoConflict())
			assertEquals("owner edited A", client.readScene(a))
			assertConverged(shared.name, client)
			clientEntityHashes(client)
		}
		on(ownerDevice) {
			assertEquals(ProjectSyncOutcome.Success, fullSync().projects[shared.name])
			val client = openClient(shared.name)
			assertEquals("co-author edited B", client.readScene(b))
			assertEquals("owner edited A", client.readScene(a))
			assertConverged(shared.name, client)
			assertEquals(coAuthorHashes, clientEntityHashes(client))
			assertResyncSilent(client)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `the second to edit a scene gets a conflict, and the resolution reaches the owner`() = runBlocking {
		val shared = shareProject("same scene")
		val a = shared.sceneIds.first()

		on(ownerDevice) {
			val client = openClient(shared.name)
			client.writeScene(a, "owner's version")
			assertTrue(client.syncNoConflict())
		}
		val coAuthorHashes = on(coAuthorDevice) {
			val client = openClient(shared.name)
			client.writeScene(a, "co-author's version")
			val conflicts = mutableListOf<ApiProjectEntity>()
			assertTrue(client.sync(resolveConflict = { server ->
				conflicts += server
				(server as ApiProjectEntity.SceneEntity).copy(content = "co-author's version")
			}))
			val conflict = assertNotNull(conflicts.singleOrNull()) as ApiProjectEntity.SceneEntity
			assertEquals(a, conflict.id)
			assertEquals("owner's version", conflict.content)
			assertEquals("co-author's version", client.readScene(a))
			assertConverged(shared.name, client)
			clientEntityHashes(client)
		}
		on(ownerDevice) {
			val client = openClient(shared.name)
			assertTrue(client.syncNoConflict())
			assertEquals("co-author's version", client.readScene(a))
			assertEquals(coAuthorHashes, clientEntityHashes(client))
			assertResyncSilent(client)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `a removed co-author's copy is parked, and a re-invite resumes it both ways`() = runBlocking {
		val shared = shareProject("remove and reinvite")
		val (a, b) = shared.sceneIds

		removeMember(shared.id)
		assertFalse(isServerMember(shared.id))

		on(coAuthorDevice) {
			assertEquals(ProjectSyncOutcome.Skipped, fullSync().projects[shared.name])
			val def = assertParked(shared.name)
			assertTrue(fileSystem.exists(def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME))
			val client = openClient(shared.name)
			assertEquals("owner wrote scene 1", client.readScene(a))
			client.writeScene(b, "co-author wrote while parked")
		}
		on(ownerDevice) {
			val client = openClient(shared.name)
			client.writeScene(a, "owner wrote while the co-author was away")
			assertTrue(client.syncNoConflict())
		}

		invite(shared.id)
		accept(shared.id)

		on(coAuthorDevice) {
			assertEquals(ProjectSyncOutcome.Success, fullSync().projects[shared.name])
			val def = assertNotNull(localProject(shared.name))
			assertTrue(localInfo(def).syncEnabled)
			val client = openClient(shared.name)
			assertEquals("owner wrote while the co-author was away", client.readScene(a))
			assertEquals("co-author wrote while parked", client.readScene(b))
			assertConverged(shared.name, client)
		}
		on(ownerDevice) {
			val client = openClient(shared.name)
			assertTrue(client.syncNoConflict())
			assertEquals("co-author wrote while parked", client.readScene(b))
			assertConverged(shared.name, client)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `a co-author who leaves keeps a parked copy and the owner's copy is untouched`() = runBlocking {
		val shared = shareProject("leave")

		on(coAuthorDevice) {
			val def = assertNotNull(localProject(shared.name))
			assertTrue(get<ProjectsService>().leaveProject(def))
			val result = fullSync()
			assertTrue(result.accountSuccess)
			assertEquals(ProjectSyncOutcome.Skipped, result.projects[shared.name])
			assertParked(shared.name)
			val client = openClient(shared.name)
			assertEquals("owner wrote scene 1", client.readScene(shared.sceneIds.first()))
		}

		assertFalse(isServerMember(shared.id))
		assertNotNull(serverProjectRowId(shared.id))
		assertFalse(
			database().serverDatabase.deletedProjectQueries.hasDeletedProject(coAuthor.userId, shared.id.id).executeAsOne()
		)

		on(ownerDevice) {
			val result = fullSync()
			assertTrue(result.allSuccess)
			assertTrue(result.projects[shared.name] in syncedOutcomes)
			val client = openClient(shared.name)
			assertTrue(localInfo(client.projectDef).syncEnabled)
			assertEquals("owner wrote scene 1", client.readScene(shared.sceneIds.first()))
			assertConverged(shared.name, client)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `the owner deleting the project parks the co-author's copy and never deletes it`() = runBlocking {
		val shared = shareProject("owner deletes")

		on(ownerDevice) {
			val def = assertNotNull(localProject(shared.name))
			assertTrue(get<ProjectsService>().deleteProject(def))
			assertTrue(fullSync().accountSuccess)
			assertNull(localProject(shared.name))
		}
		assertNull(serverProjectRowId(shared.id))

		on(coAuthorDevice) {
			repeat(2) { assertTrue(fullSync().accountSuccess) }
			val def = assertParked(shared.name)
			assertTrue(fileSystem.exists(def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME))
			val client = openClient(shared.name)
			shared.sceneIds.forEachIndexed { i, id -> assertEquals("owner wrote scene ${i + 1}", client.readScene(id)) }
		}
	}

	@Test
	@Timeout(value = 180)
	fun `writing stats group words by author on both sides`() = runBlocking {
		val shared = shareProject("stats by author")
		val (a, b) = shared.sceneIds

		val ownerWords = on(ownerDevice) {
			val client = openClient(shared.name)
			val words = client.typeInto(a, "owner wrote scene 1", "owner wrote scene 1 and then four more")
			assertTrue(client.syncNoConflict())
			words
		}
		val coAuthorWords = on(coAuthorDevice) {
			val client = openClient(shared.name)
			val words = client.typeInto(b, "owner wrote scene 2", "owner wrote scene 2 plus two")
			assertTrue(client.syncNoConflict())
			words
		}
		assertTrue(ownerWords > 0 && coAuthorWords > 0)

		val expected = setOf(
			Triple(owner.userId, OWNER_PEN_NAME, ownerWords),
			Triple(coAuthor.userId, CO_AUTHOR_PEN_NAME, coAuthorWords),
		)
		on(ownerDevice) {
			val client = openClient(shared.name)
			assertTrue(client.syncNoConflict())
			assertEquals(expected, client.wordsPerAuthor())
		}
		on(coAuthorDevice) {
			val client = openClient(shared.name)
			assertTrue(client.syncNoConflict())
			assertEquals(expected, client.wordsPerAuthor())
		}

		val rowId = assertNotNull(serverProjectRowId(shared.id))
		val slots = database().serverDatabase.writingActivityQueries.getAllForProject(owner.userId, rowId).executeAsList()
		assertEquals(
			setOf(
				Triple(ownerDevice.install.installId, owner.userId, OWNER_PEN_NAME),
				Triple(coAuthorDevice.install.installId, coAuthor.userId, CO_AUTHOR_PEN_NAME),
			),
			slots.map { Triple(it.device_id, it.author_id, it.author_name) }.toSet(),
		)
	}

	@Test
	@Timeout(value = 180)
	fun `a co-author's two devices both get the project and share edits through the owner's copy`() = runBlocking {
		val phone = newDevice(coAuthor, "coauthor-phone")
		val shared = shareProject("two devices")
		val a = shared.sceneIds.first()

		on(phone) {
			assertTrue(fullSync().allSuccess)
			val def = assertNotNull(localProject(shared.name))
			assertEquals(owner.userId, localInfo(def).ownerId)
			val client = openClient(shared.name)
			assertEquals("owner wrote scene 1", client.readScene(a))
			client.writeScene(a, "written on the phone")
			assertTrue(client.syncNoConflict())
		}
		on(coAuthorDevice) {
			assertEquals(ProjectSyncOutcome.Success, fullSync().projects[shared.name])
			val client = openClient(shared.name)
			assertEquals("written on the phone", client.readScene(a))
			assertConverged(shared.name, client)
		}
		on(ownerDevice) {
			val client = openClient(shared.name)
			assertTrue(client.syncNoConflict())
			assertEquals("written on the phone", client.readScene(a))
			assertConverged(shared.name, client)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `the owner's sync switch holds back a co-author's edit until it is turned on`() = runBlocking {
		val shared = shareProject("owner switch")
		val a = shared.sceneIds.first()

		on(ownerDevice) {
			val def = assertNotNull(localProject(shared.name))
			assertEquals(ProjectSyncToggle.Result.Done, get<ProjectSyncToggle>().turnOff(def))
		}
		on(coAuthorDevice) {
			val client = openClient(shared.name)
			client.writeScene(a, "co-author edited while the owner was off")
			assertTrue(client.syncNoConflict())
		}
		on(ownerDevice) {
			val result = fullSync()
			assertTrue(result.accountSuccess)
			assertEquals(ProjectSyncOutcome.Skipped, result.projects[shared.name])
			val def = assertNotNull(localProject(shared.name))
			assertFalse(localInfo(def).syncEnabled)
			val client = openClient(shared.name)
			assertEquals("owner wrote scene 1", client.readScene(a))
			client.close()

			assertEquals(ProjectSyncToggle.Result.Done, get<ProjectSyncToggle>().turnOn(def))
			assertEquals(ProjectSyncOutcome.Success, fullSync().projects[shared.name])
			val reopened = openClient(shared.name)
			assertEquals("co-author edited while the owner was off", reopened.readScene(a))
			assertConverged(shared.name, reopened)
		}
	}

	@Test
	@Timeout(value = 180)
	fun `turning sync on for a project another install deleted keeps it under a new id`() = runBlocking {
		val laptop = newDevice(owner, "owner-laptop")
		val name = "deleted elsewhere"
		val (oldId, sceneId) = on(ownerDevice) {
			val client = newClient(name)
			val scene = assertNotNull(client.sceneEditor.createScene(parent = null, sceneName = "Kept"))
			client.writeScene(scene.id, "written before the delete")
			assertTrue(client.syncNoConflict())
			assertTrue(fullSync().allSuccess)
			val def = assertNotNull(localProject(name))
			assertEquals(ProjectSyncToggle.Result.Done, get<ProjectSyncToggle>().turnOff(def))
			def.id to scene.id
		}
		on(laptop) {
			assertTrue(fullSync().allSuccess)
			assertTrue(get<ProjectsService>().deleteProject(assertNotNull(localProject(name))))
			assertTrue(fullSync().accountSuccess)
		}
		assertNull(serverProjectRowId(oldId))

		on(ownerDevice) {
			assertEquals(ProjectSyncToggle.Result.Done, get<ProjectSyncToggle>().turnOn(assertNotNull(localProject(name))))
			val result = fullSync()
			assertTrue(result.accountSuccess)
			assertEquals(ProjectSyncOutcome.Success, result.projects[name])
			val def = assertNotNull(localProject(name))
			assertNotEquals(oldId, def.id)
			assertTrue(localInfo(def).syncEnabled)
			assertNotNull(serverProjectRowId(def.id))
			val client = openClient(name)
			assertEquals("written before the delete", client.readScene(sceneId))
			assertConverged(name, client)
		}
	}

	/** A shared project kept here with sync off: owner marks intact. */
	private fun assertParked(name: String): ProjectDef {
		val def = assertNotNull(localProject(name))
		val info = localInfo(def)
		assertFalse(info.syncEnabled)
		assertEquals(owner.userId, info.ownerId)
		assertEquals(OWNER_PEN_NAME, info.ownerName)
		return def
	}

	/** Types [after] over [before] in the editor; returns the words the session tracker credited. */
	private suspend fun HeadlessClient.typeInto(sceneId: Int, before: String, after: String): Int {
		val tracker: WritingSessionTracker = scope.get()
		tracker.rememberBaseline(sceneId, before)
		writeScene(sceneId, after)
		return tracker.onSceneSaved(sceneId, after, UpdateSource.Editor)
	}

	/** Author id, name and words, as the stats screen groups them. */
	private suspend fun HeadlessClient.wordsPerAuthor(): Set<Triple<Long?, String?, Int>> =
		scope.get<StatisticsService>().recalculateStatistics { ReferenceIndex() }.wordsPerAuthor
			.map { Triple(it.authorId, it.authorName, it.words) }
			.toSet()
}
