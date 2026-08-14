package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectdata.saveStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import okio.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A project's sync baseline (the entity journal and `lastSyncedHash`) records what a *specific*
 * server has already confirmed. Carrying it onto a different server makes an untouched project
 * look "already in sync", so its content is never pushed and the local copy fast-forwards to the
 * new server's empty one.
 */
class ProjectsRepositorySyncBaselineTest : ProjectsRepositoryBaseTest() {

	private val projectDef = getProjectDef(PROJECT_1_NAME)

	private fun syncJournalPath(): Path =
		projectDef.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME

	private fun seedBaseline() {
		ffs.write(syncJournalPath()) { writeUtf8("""{"lastId":42}""") }
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

	private fun storedProjectData(): StoredProjectData =
		readStoredProjectData(projectDef, ffs, toml)

	@Test
	fun `Moving a project to a different server id clears its sync baseline`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		seedBaseline()
		val repo = projectsRepository()

		repo.setProjectId(projectDef, ProjectId("id-on-the-new-server"))

		assertFalse(ffs.exists(syncJournalPath()), "the entity journal must not survive the move")
		assertNull(
			storedProjectData().lastSyncedHash,
			"the project-data baseline must not survive the move",
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

		assertTrue(ffs.exists(syncJournalPath()), "an unchanged id is not a server change")
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

		assertFalse(ffs.exists(syncJournalPath()))
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
}
