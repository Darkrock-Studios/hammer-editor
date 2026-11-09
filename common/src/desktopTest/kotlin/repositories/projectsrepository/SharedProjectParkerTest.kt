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

		val before = projectsMetaDatasource.readMetadata(def)!!.info

		val parker = SharedProjectParker(repo, datasource)
		assertTrue(parker.park(def))

		assertEquals(before.copy(syncEnabled = false), projectsMetaDatasource.readMetadata(def)!!.info)
		assertTrue(datasource.load().projectBaselines.isEmpty())
		assertEquals(journalJson, ffs.read(journal) { readUtf8() })
		// Logged once: a second park finds sync already off.
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
	fun `Resuming turns sync on and keeps the journal as it is`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		val journal = def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
		ffs.write(journal) { writeUtf8(journalJson) }
		SharedProjectParker(repo, accountSyncDatasource()).park(def)

		val parked = ffs.read(journal) { readUtf8() }

		assertTrue(repo.resumeSync(def))

		assertTrue(repo.isSyncEnabled(def))
		assertEquals(parked, ffs.read(journal) { readUtf8() })
		val data = json.decodeFromString<ProjectSynchronizationData>(parked)
		assertFalse(data.verifyAllEntities)
		assertEquals(listOf(3), data.newIds)
	}

	@Test
	fun `An open parked project is not resumed`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		SharedProjectParker(repo, accountSyncDatasource()).park(def)

		val projectScope = createProjectScope(def)
		try {
			assertThrows<ProjectInUseException> { repo.resumeSync(def) }
		} finally {
			projectScope.close()
		}
		assertFalse(repo.isSyncEnabled(def))
	}
}
