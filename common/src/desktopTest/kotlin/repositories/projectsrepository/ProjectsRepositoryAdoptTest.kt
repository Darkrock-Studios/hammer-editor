package repositories.projectsrepository

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import getProjectsDirectory
import okio.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectsRepositoryAdoptTest : ProjectsRepositoryBaseTest() {
	private val projectDef = getProjectDef(PROJECT_1_NAME)
	private val serverId = ProjectId("5d0c2b7e-9a41-4f3e-8b6d-1e2f3a4b5c6d")

	private fun metadataPath(): Path = projectDef.path.toOkioPath() / ProjectMetadata.FILENAME

	private fun contentFiles(): List<Path> =
		ffs.listRecursively(projectDef.path.toOkioPath())
			.filter { it.name != ProjectMetadata.FILENAME && it.name != SyncJournalDatasource.SYNC_FILE_NAME }
			.toList()
			.sorted()

	private fun journalPath(): Path = projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME

	private fun readJournal(): ProjectSynchronizationData =
		ffs.read(journalPath()) { json.decodeFromString(readUtf8()) }

	private fun createUnmarkedProject() {
		createProject(ffs, PROJECT_1_NAME)
		ffs.delete(metadataPath())
	}

	@Test
	fun `Adopting a folder that lost its project toml lists it again under the given id`() {
		createUnmarkedProject()
		val contentBefore = contentFiles()
		val repo = projectsRepository()
		assertTrue(repo.getProjects().none { it.name == PROJECT_1_NAME })

		val adopted = repo.adoptUnmarkedDirectory(PROJECT_1_NAME, serverId)

		assertEquals(projectDef, adopted)
		assertEquals(projectDef, repo.findProject(serverId))
		assertEquals(contentBefore, contentFiles())
		// Whatever format the content is in, every migration runs over it.
		assertEquals(0, projectsMetaDatasource.loadMetadata(projectDef).info.dataVersion)
	}

	@Test
	fun `An adopted folder is flagged for a full conflict check`() {
		createUnmarkedProject()

		projectsRepository().adoptUnmarkedDirectory(PROJECT_1_NAME, serverId)

		// Without the flag, a rebuilt journal would list every entity as clean and the server's
		// copy of each would silently replace the folder's.
		assertTrue(readJournal().verifyAllEntities)
	}

	@Test
	fun `An adopted folder keeps the baseline its old journal recorded`() {
		createUnmarkedProject()
		val oldJournal = ProjectSynchronizationData(
			lastId = 3,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(1_000_000),
			dirty = emptyList(),
			deletedIds = setOf(2),
			syncedHashes = mapOf(1 to "hash-the-server-confirmed"),
		)
		ffs.write(journalPath()) { writeUtf8(json.encodeToString(oldJournal)) }

		projectsRepository().adoptUnmarkedDirectory(PROJECT_1_NAME, serverId)

		val journal = readJournal()
		assertTrue(journal.verifyAllEntities)
		assertEquals(oldJournal.syncedHashes, journal.syncedHashes)
		assertEquals(oldJournal.deletedIds, journal.deletedIds)
	}

	@Test
	fun `An empty folder is adopted`() {
		ffs.createDirectories(projectDef.path.toOkioPath())

		val adopted = projectsRepository().adoptUnmarkedDirectory(PROJECT_1_NAME, serverId)

		assertEquals(projectDef, adopted)
	}

	@Test
	fun `A folder that is not a Hammer project is not adopted`() {
		val folder = projectDef.path.toOkioPath()
		ffs.createDirectories(folder)
		ffs.write(folder / "shopping.txt") { writeUtf8("eggs") }

		assertNull(projectsRepository().adoptUnmarkedDirectory(PROJECT_1_NAME, serverId))

		assertEquals(listOf(folder / "shopping.txt"), ffs.list(folder))
	}

	@Test
	fun `A folder that still has its project toml is not adopted`() {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val ownId = repo.getProjectId(projectDef)
		val metadataBefore = ffs.read(metadataPath()) { readUtf8() }

		assertNull(repo.adoptUnmarkedDirectory(PROJECT_1_NAME, serverId))

		assertEquals(ownId, repo.getProjectId(projectDef))
		assertEquals(metadataBefore, ffs.read(metadataPath()) { readUtf8() })
	}

	@Test
	fun `Nothing is adopted when no folder has the name`() {
		val repo = projectsRepository()

		assertNull(repo.adoptUnmarkedDirectory(PROJECT_1_NAME, serverId))

		assertFalse(ffs.exists(projectDef.path.toOkioPath()))
	}

	@Test
	fun `A file with the project's name is not adopted`() {
		val repo = projectsRepository()
		val filePath = getProjectsDirectory() / PROJECT_1_NAME
		ffs.write(filePath) { writeUtf8("not a project") }

		assertNull(repo.adoptUnmarkedDirectory(PROJECT_1_NAME, serverId))

		assertTrue(ffs.metadata(filePath).isRegularFile)
	}
}
