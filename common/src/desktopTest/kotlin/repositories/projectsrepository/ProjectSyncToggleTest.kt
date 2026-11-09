package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncToggle
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SharedProjectParker
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import listedProjectId
import org.junit.jupiter.api.Test
import utils.projectsDirectoryOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectSyncToggleTest : ProjectsRepositoryBaseTest() {

	private fun accountSyncDatasource() = AccountSyncDatasource(ffs, json, projectsDirectoryOf(settingsRepo))

	@Test
	fun `Turning sync on resumes with the journal intact`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val toggle = ProjectSyncToggle(repo, accountSyncDatasource())
		val journal = def.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
		ffs.write(journal) { writeUtf8("""{"lastId":3,"newIds":[3],"lastSync":"2025-01-01T00:00:00Z","dirty":[],"deletedIds":[]}""") }
		val before = ffs.read(journal) { readUtf8() }

		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOff(def))
		assertFalse(repo.isSyncEnabled(def))
		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOn(def))

		assertTrue(repo.isSyncEnabled(def))
		assertEquals(def.id, repo.listedProjectId(def))
		assertEquals(before, ffs.read(journal) { readUtf8() })
	}

	@Test
	fun `Turning sync on for an id the account lists as deleted re-mints it`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val datasource = accountSyncDatasource()
		val toggle = ProjectSyncToggle(repo, datasource)
		toggle.turnOff(def)
		datasource.update { it.copy(deletedProjects = setOf(def.id)) }

		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOn(def))

		assertTrue(repo.isSyncEnabled(def))
		assertNotEquals(def.id, repo.listedProjectId(def))
	}

	@Test
	fun `Turning sync on for an open project the server deleted leaves it off`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val datasource = accountSyncDatasource()
		val toggle = ProjectSyncToggle(repo, datasource)
		toggle.turnOff(def)
		datasource.update { it.copy(deletedProjects = setOf(def.id)) }

		val projectScope = createProjectScope(def)
		val result = try {
			toggle.turnOn(def)
		} finally {
			projectScope.close()
		}

		assertEquals(ProjectSyncToggle.Result.DeletedOnServer, result)
		assertFalse(repo.isSyncEnabled(def))
		assertEquals(def.id, repo.listedProjectId(def))
	}

	private suspend fun parkedSharedProject(): Pair<ProjectsRepository, ProjectSyncToggle> {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val datasource = accountSyncDatasource()
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		SharedProjectParker(repo, datasource).park(def)
		return repo to ProjectSyncToggle(repo, datasource)
	}

	@Test
	fun `Turning sync on for a parked shared project asks first and changes nothing`() = scope.runTest {
		val (repo, toggle) = parkedSharedProject()
		val def = getProjectDef(PROJECT_1_NAME)

		assertEquals(ProjectSyncToggle.Result.NeedsConfirmation, toggle.turnOn(def))

		assertFalse(repo.isSyncEnabled(def))
		assertEquals(ProjectOwner(7L, "Alice"), repo.getOwner(def))
		assertEquals(def.id, repo.listedProjectId(def))
	}

	@Test
	fun `Uploading a parked shared project as one's own clears the owner and re-mints it`() = scope.runTest {
		val (repo, toggle) = parkedSharedProject()
		val def = getProjectDef(PROJECT_1_NAME)

		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOn(def, uploadAsOwn = true))

		assertTrue(repo.isSyncEnabled(def))
		assertNull(repo.getOwner(def))
		assertFalse(repo.isParked(def))
		assertNotEquals(def.id, repo.listedProjectId(def))
	}

	@Test
	fun `Uploading an open parked shared project as one's own retires its id for account sync`() = scope.runTest {
		val (repo, toggle) = parkedSharedProject()
		val def = getProjectDef(PROJECT_1_NAME)

		val projectScope = createProjectScope(def)
		val result = try {
			toggle.turnOn(def, uploadAsOwn = true)
		} finally {
			projectScope.close()
		}

		assertEquals(ProjectSyncToggle.Result.OwnNeedsClose, result)
		assertNull(repo.getOwner(def))
		assertFalse(repo.isSyncEnabled(def))
		assertEquals(def.id, repo.listedProjectId(def))
		assertEquals(setOf(def.id), accountSyncDatasource().load().deletedProjects)
	}

	@Test
	fun `Turning sync on for a shared project turned off by hand only resumes it`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val def = getProjectDef(PROJECT_1_NAME)
		val repo = projectsRepository()
		val datasource = accountSyncDatasource()
		val toggle = ProjectSyncToggle(repo, datasource)
		repo.setOwner(def, ProjectOwner(7L, "Alice"))
		toggle.turnOff(def)
		datasource.update { it.copy(deletedProjects = setOf(def.id)) }

		assertEquals(ProjectSyncToggle.Result.Done, toggle.turnOn(def))

		assertTrue(repo.isSyncEnabled(def))
		assertEquals(def.id, repo.listedProjectId(def))
		assertEquals(ProjectOwner(7L, "Alice"), repo.getOwner(def))
	}
}
