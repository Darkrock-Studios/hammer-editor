package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.saveStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A project's sync baseline (the entity journal's confirmed hashes and `lastSyncedHash`) records
 * what a *specific* server has already confirmed. Carrying it onto a different server makes an
 * untouched project look "already in sync", so its content is never pushed and the local copy
 * fast-forwards to the new server's empty one. Local pending work must survive the clear, though:
 * the new server has never seen it either.
 */
class ProjectsRepositorySyncBaselineTest : ProjectsRepositoryBaseTest() {

	private val projectDef = getProjectDef(PROJECT_1_NAME)

	private fun syncJournalPath(): Path =
		projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME

	private val seededJournal = ProjectSynchronizationData(
		currentSyncId = "session-on-the-old-server",
		lastId = 42,
		newIds = listOf(41, 42),
		lastSync = Instant.fromEpochSeconds(1_000_000),
		dirty = listOf(EntityOriginalState(5, "hash-agreed-with-the-old-server")),
		deletedIds = setOf(7),
		syncedHashes = mapOf(5 to "hash-agreed-with-the-old-server"),
		cachedProjectHash = "project-hash-agreed-with-the-old-server",
		hashAlgoVersion = 3,
	)

	private fun seedBaseline() {
		ffs.write(syncJournalPath()) { writeUtf8(json.encodeToString(seededJournal)) }
		saveStoredProjectData(
			projectDef,
			ffs,
			toml,
			StoredProjectData(
				data = ProjectData(authorName = "Author"),
				lastSyncedHash = "hash-agreed-with-the-old-server",
			),
		)
	}

	private fun readJournal(): ProjectSynchronizationData =
		ffs.read(syncJournalPath()) { json.decodeFromString(readUtf8()) }

	private fun storedProjectData(): StoredProjectData =
		readStoredProjectData(projectDef, ffs, toml)

	@Test
	fun `Moving a project to a different server id drops what the old server confirmed`() =
		scope.runTest {
			createProject(ffs, PROJECT_1_NAME)
			seedBaseline()
			val repo = projectsRepository()

			repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

			val journal = readJournal()
			assertEquals(emptyMap(), journal.syncedHashes, "confirmed hashes were the old server's")
			assertNull(journal.cachedProjectHash)
			assertEquals(0, journal.hashAlgoVersion)
			assertNull(journal.currentSyncId, "the sync session belonged to the old server")
			assertEquals(Instant.DISTANT_PAST, journal.lastSync)
			assertNull(
				storedProjectData().lastSyncedHash,
				"the project-data baseline must not survive the move",
			)
		}

	@Test
	fun `Moving a project flagged for verification keeps its conflict baselines`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		ffs.write(syncJournalPath()) {
			writeUtf8(json.encodeToString(seededJournal.copy(verifyAllEntities = true)))
		}
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		// Without them, the verification pass baselines each pending edit on its own local hash, and
		// the server rejects the upload as a conflict.
		val journal = readJournal()
		assertTrue(journal.verifyAllEntities)
		assertEquals(seededJournal.syncedHashes, journal.syncedHashes)
		assertEquals(seededJournal.dirty, journal.dirty)
		assertNull(journal.currentSyncId)
		assertEquals(Instant.DISTANT_PAST, journal.lastSync)
		assertNull(journal.cachedProjectHash)
		assertEquals(0, journal.hashAlgoVersion)
	}

	@Test
	fun `Moving a project keeps local work the new server has never seen`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		seedBaseline()
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		val journal = readJournal()
		assertEquals(seededJournal.lastId, journal.lastId)
		assertEquals(seededJournal.newIds, journal.newIds)
		assertEquals(
			seededJournal.deletedIds,
			journal.deletedIds,
			"a tombstone the old server never got must still be pushed to the new one",
		)
		assertEquals(
			listOf(EntityOriginalState(5, originalHash = null)),
			journal.dirty,
			"the edit is still pending, but its baseline was agreed with the old server",
		)
	}

	@Test
	fun `Clearing the sync baseline preserves the project data itself`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		seedBaseline()
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		assertEquals(
			ProjectData(authorName = "Author"),
			storedProjectData().data,
			"only the baseline is stale; the user's data must be left alone so it can be uploaded",
		)
	}

	@Test
	fun `Re-writing the same project id leaves the sync baseline alone`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val sameId = ProjectId("id-already-recorded")
		val repo = projectsRepository()
		repo.setProjectId(projectDef, sameId)
		seedBaseline()

		repo.setProjectId(projectDef, sameId)

		assertEquals(seededJournal, readJournal(), "an unchanged id is not a server change")
		assertEquals("hash-agreed-with-the-old-server", storedProjectData().lastSyncedHash)
	}

	@Test
	fun `Clearing all sync baselines resets each project but keeps its id`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val projectId = repo.getProjectId(projectDef)
		seedBaseline()

		// A server change: whatever the project is uploaded to next has never seen it.
		repo.clearAllSyncBaselines()

		assertEquals(emptyMap(), readJournal().syncedHashes)
		assertEquals(Instant.DISTANT_PAST, readJournal().lastSync)
		assertNull(storedProjectData().lastSyncedHash)
		assertEquals(ProjectData(authorName = "Author"), storedProjectData().data)
		assertEquals(projectId, repo.getProjectId(projectDef))
	}

	@Test
	fun `Clearing the baseline of a never-synced project is a no-op`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("first-ever-id"))

		// No journal is fabricated, and no settings.toml is written just to hold a null.
		assertFalse(ffs.exists(syncJournalPath()))
		assertFalse(ffs.exists(projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME))
	}

	@Test
	fun `Clearing the baseline of an open project drops its cached copy too`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		seedBaseline()
		val projectDataRepository = openProject()
		// An open project serves project data from memory, so the stale hash has to go from there
		// as well or the next sync still fast-forwards onto the new server's empty copy.
		assertEquals("hash-agreed-with-the-old-server", projectDataRepository.load().lastSyncedHash)

		projectsRepository().setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		assertNull(projectDataRepository.load().lastSyncedHash)
		assertNull(storedProjectData().lastSyncedHash)
		assertEquals(ProjectData(authorName = "Author"), projectDataRepository.load().data)
	}

	/** Mirrors the project scope `mainModule` opens when a project is opened in the editor. */
	private fun openProject(): ProjectDataRepository {
		setupKoin(
			module {
				scope<ProjectDefScope> {
					scoped<ProjectDef> { get<ProjectDefScope>().projectDef }
					scoped { ProjectDataDatasource(ffs, toml, get()) }
					scoped { ProjectDataRepository(get(), get()) }
				}
			}
		)
		return createProjectScope(projectDef).get()
	}

	private fun backupService(repo: ProjectsRepository): ProjectBackupService {
		every { settings.maxBackups } returns 5
		val clock = object : Clock {
			override fun now(): Instant = Instant.parse("2025-12-28T16:29:00Z")
		}
		return ProjectBackupService(ffs, repo, settingsRepo, clock, mockk(relaxed = true))
	}

	@Test
	fun `Restoring a backup over the same project flags it for a full conflict check`() =
		scope.runTest {
			createProject(ffs, PROJECT_1_NAME)
			val repo = projectsRepository()
			val projectId = repo.getProjectId(projectDef)
			seedBaseline()
			val backups = backupService(repo)
			val backup = assertNotNull(backups.createBackup(projectDef))

			assertTrue(backups.restoreBackup(backup, projectDef.path))

			assertEquals(projectId, repo.getProjectId(projectDef))
			val journal = readJournal()
			assertTrue(journal.verifyAllEntities, "the next sync must conflict-check every entity")
			assertNull(journal.cachedProjectHash, "the pre-sync probe must not skip the restored project")
			assertNull(journal.currentSyncId)
			// Null baselines would let the restored copy overwrite the server unchecked. The backup's
			// confirmed hashes are what make a newer server copy raise a conflict.
			assertEquals(seededJournal.syncedHashes, journal.syncedHashes)
			assertEquals(seededJournal.dirty, journal.dirty)
			assertNull(storedProjectData().lastSyncedHash, "restored project data must not fast-forward")
			assertEquals(ProjectData(authorName = "Author"), storedProjectData().data)
		}

	@Test
	fun `Restoring a backup under a fresh id keeps its conflict baselines`() =
		scope.runTest {
			createProject(ffs, PROJECT_1_NAME)
			val repo = projectsRepository()
			val backupId = repo.getProjectId(projectDef)
			seedBaseline()
			val backups = backupService(repo)
			val backup = assertNotNull(backups.createBackup(projectDef))
			ffs.deleteRecursively(projectDef.path.toOkioPath())

			assertTrue(backups.restoreBackup(backup, projectDef.path))

			// A fresh id can still adopt a same-named server project, where these baselines raise its
			// conflicts. A new, empty server project holds no entities to conflict-check against.
			assertNotEquals(backupId, repo.getProjectId(projectDef))
			val journal = readJournal()
			assertEquals(seededJournal.syncedHashes, journal.syncedHashes)
			assertEquals(seededJournal.dirty, journal.dirty)
			assertEquals(Instant.DISTANT_PAST, journal.lastSync)
			assertNull(journal.cachedProjectHash)
			assertTrue(journal.verifyAllEntities)
			assertNull(storedProjectData().lastSyncedHash)
		}

	@Test
	fun `Resetting a restored project that never synced fabricates no journal`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)

		projectsRepository().resetSyncStateAfterRestore(projectDef, serverKnowsProject = false)

		assertFalse(ffs.exists(syncJournalPath()))
	}

	@Test
	fun `Restoring a journal-less backup over a synced project flags a fresh journal`() =
		scope.runTest {
			createProject(ffs, PROJECT_1_NAME)
			val repo = projectsRepository()
			val backups = backupService(repo)
			val backup = assertNotNull(backups.createBackup(projectDef))
			seedBaseline()

			assertTrue(backups.restoreBackup(backup, projectDef.path))

			// A rebuilt journal would list every entity as clean, so the server's copy would win.
			val journal = readJournal()
			assertTrue(journal.verifyAllEntities)
			assertEquals(emptyMap(), journal.syncedHashes)
			assertEquals(emptyList(), journal.dirty)
			assertEquals(emptyList(), journal.newIds)
			assertEquals(emptySet(), journal.deletedIds)
		}

	@Test
	fun `A corrupt journal is discarded so the next sync rebuilds it`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		ffs.write(syncJournalPath()) { writeUtf8("not json at all") }
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		assertFalse(ffs.exists(syncJournalPath()))
		assertTrue(ffs.exists(projectDef.path.toOkioPath()))
	}
}
