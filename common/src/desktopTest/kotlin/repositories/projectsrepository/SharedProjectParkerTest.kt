package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectInUseException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SharedProjectParker
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import utils.projectsDirectoryOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedProjectParkerTest : ProjectsRepositoryBaseTest() {

	private val journalJson =
		"""{"lastId":3,"newIds":[3],"lastSync":"2025-01-01T00:00:00Z","dirty":[],"deletedIds":[]}"""

	private fun accountSyncDatasource() = AccountSyncDatasource(ffs, json, projectsDirectoryOf(settingsRepo))

	@Test
	fun `Parking turns sync off and drops the baseline, keeping the folder and journal`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val datasource = accountSyncDatasource()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		datasource.update { it.copy(projectBaselines = mapOf(def.id to ProjectBaseline(def.name))) }
		val journal = def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
		ffs.write(journal) { writeUtf8(journalJson) }

		val parker = SharedProjectParker(repo, datasource)
		assertTrue(parker.park(def))

		assertFalse(repo.isSyncEnabled(def))
		assertTrue(repo.isParked(def))
		assertTrue(datasource.load().projectBaselines.isEmpty())
		assertEquals(journalJson, ffs.read(journal) { readUtf8() })
		// Logged once: a second park finds it parked.
		assertFalse(parker.park(def))
	}

	@Test
	fun `A project of the account's own is never parked`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()

		assertFalse(SharedProjectParker(repo, accountSyncDatasource()).park(def))
		assertTrue(repo.isSyncEnabled(def))
	}

	@Test
	fun `Unparking turns sync on and flags the journal for a full check`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		val journal = def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
		ffs.write(journal) { writeUtf8(journalJson) }
		SharedProjectParker(repo, accountSyncDatasource()).park(def)

		assertTrue(repo.unpark(def))

		assertTrue(repo.isSyncEnabled(def))
		assertFalse(repo.isParked(def))
		val data = json.decodeFromString<ProjectSynchronizationData>(ffs.read(journal) { readUtf8() })
		assertTrue(data.verifyAllEntities)
		assertEquals(listOf(3), data.newIds)
	}

	@Test
	fun `An open parked project is not unparked`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		SharedProjectParker(repo, accountSyncDatasource()).park(def)

		val projectScope = createProjectScope(def)
		try {
			assertThrows<ProjectInUseException> { repo.unpark(def) }
		} finally {
			projectScope.close()
		}
		assertTrue(repo.isParked(def))
	}
}
