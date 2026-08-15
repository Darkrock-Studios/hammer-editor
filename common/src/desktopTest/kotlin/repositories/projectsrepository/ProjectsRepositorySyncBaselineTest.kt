package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.saveStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.Path
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
		projectDef.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME

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
	fun `Removing a project id clears its sync baseline`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		seedBaseline()
		val repo = projectsRepository()

		// removeServer() and create-account both clear ids; whatever the project is uploaded to
		// next has never seen it.
		repo.removeProjectId(projectDef)

		assertEquals(emptyMap(), readJournal().syncedHashes)
		assertNull(storedProjectData().lastSyncedHash)
		assertEquals(ProjectData(authorName = "Author"), storedProjectData().data)
	}

	@Test
	fun `Clearing the baseline of a never-synced project is a no-op`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("first-ever-id"))

		// No journal is fabricated, and no project_data.toml is written just to hold a null.
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
