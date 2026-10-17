package repositories.projectsrepository

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.GlobalMigration
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration3_4
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import getProjectsDirectory
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import okio.Path
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectsRepositoryAdoptTest : ProjectsRepositoryBaseTest() {
	private val project2Id = ProjectId("8c3f6a2e-4b1d-4e5a-9f7c-2d6b1a0e9c43")

	private fun dir(def: ProjectDef): Path = def.path.toOkioPath()

	private fun createFolder(name: String, vararg subdirectories: String): ProjectDef {
		val def = getProjectDef(name)
		ffs.createDirectories(dir(def))
		subdirectories.forEach { ffs.createDirectories(dir(def) / it) }
		return def
	}

	/** The heal mints the id, so only the folder is known up front. */
	private fun assertHealed(expected: ProjectDef, healed: List<ProjectDef>) {
		val def = healed.single()
		assertEquals(expected.copy(id = def.id), def)
		assertEquals(def.id, projectsMetaDatasource.loadMetadata(def).info.projectId)
	}

	private fun isMarked(def: ProjectDef): Boolean = ffs.exists(dir(def) / ProjectMetadata.FILENAME)

	private fun journalPath(def: ProjectDef): Path = dir(def) / SyncJournalDatasource.SYNC_FILE_NAME

	private fun readJournal(def: ProjectDef): ProjectSynchronizationData =
		ffs.read(journalPath(def)) { json.decodeFromString(readUtf8()) }

	private fun migrator(repo: ProjectsRepository) =
		object : DataMigrator(settingsRepo, repo, projectsMetaDatasource) {
			override val latestProjectDataVersion = 4
			override fun getGlobalMigrations(): List<GlobalMigration> = emptyList()
			override fun getMigrators(): Map<Int, Migration> =
				(1..3).associateWith { noOpMigration(it) } + (4 to Migration3_4(ffs, projectsMetaDatasource))
		}

	private fun noOpMigration(version: Int) = object : Migration {
		override val toVersion = version
		override fun migrate(projectDef: ProjectDef) {}
	}

	@Test
	fun `Heal re-marks a folder that still holds scenes`() = scope.runTest {
		val repo = projectsRepository()
		val lost = createFolder("Lost Project", SceneDatasource.SCENE_DIRECTORY)

		val healed = repo.healUnmarkedProjectDirectories()

		assertHealed(lost, healed)
		assertEquals(0, projectsMetaDatasource.loadMetadata(lost).info.dataVersion)
		assertTrue(healed.single() in repo.getProjects())
	}

	@Test
	fun `Heal re-marks a folder holding only a sync journal and flags it for a full conflict check`() =
		scope.runTest {
			val repo = projectsRepository()
			val lost = createFolder("Synced Once")
			val journal = ProjectSynchronizationData(
				lastId = 3,
				newIds = emptyList(),
				lastSync = Instant.fromEpochSeconds(1_000_000),
				dirty = emptyList(),
				deletedIds = emptySet(),
				syncedHashes = mapOf(1 to "confirmed-hash"),
			)
			ffs.write(journalPath(lost)) { writeUtf8(json.encodeToString(journal)) }

			assertHealed(lost, repo.healUnmarkedProjectDirectories())

			val healedJournal = readJournal(lost)
			assertTrue(healedJournal.verifyAllEntities, "the journal may be stale, so every entity is checked")
			assertEquals(journal.syncedHashes, healedJournal.syncedHashes)
		}

	@Test
	fun `A healed folder keeps its pending edit's baseline when it adopts a server project's id`() =
		scope.runTest {
			val repo = projectsRepository()
			val lost = createFolder("Synced Once", SceneDatasource.SCENE_DIRECTORY)
			val journal = ProjectSynchronizationData(
				currentSyncId = "stale-session",
				lastId = 36,
				newIds = emptyList(),
				lastSync = Instant.fromEpochSeconds(1_000_000),
				dirty = listOf(EntityOriginalState(36, "server-confirmed-hash")),
				deletedIds = emptySet(),
				syncedHashes = mapOf(36 to "server-confirmed-hash", 1 to "other-confirmed-hash"),
				cachedProjectHash = "stale-project-hash",
				hashAlgoVersion = 3,
			)
			ffs.write(journalPath(lost)) { writeUtf8(json.encodeToString(journal)) }
			val healed = repo.healUnmarkedProjectDirectories().single()

			repo.setProjectId(healed, ProjectId("server-project-id"))

			// The pending edit must upload against the hash the server confirmed, not conflict.
			val adoptedJournal = readJournal(healed)
			assertTrue(adoptedJournal.verifyAllEntities)
			assertEquals(journal.syncedHashes, adoptedJournal.syncedHashes)
			assertEquals(journal.dirty, adoptedJournal.dirty)
			assertNull(adoptedJournal.currentSyncId)
			assertNull(adoptedJournal.cachedProjectHash)
			assertEquals(0, adoptedJournal.hashAlgoVersion)
		}

	@Test
	fun `Heal leaves alone folders that are empty, hidden, invalidly named or already marked`() =
		scope.runTest {
			val repo = projectsRepository()
			createProject(ffs, PROJECT_2_NAME)
			val empty = createFolder("Empty Folder")
			val hidden = createFolder(".hidden", SceneDatasource.SCENE_DIRECTORY)
			val invalid = createFolder("bad~name", SceneDatasource.SCENE_DIRECTORY)
			// A project.toml in any form is the metadata datasource's to recover, not the heal's.
			val oddMarker = createFolder("Odd Marker", SceneDatasource.SCENE_DIRECTORY, ProjectMetadata.FILENAME)
			val lost = createFolder("Lost Project", SceneDatasource.SCENE_DIRECTORY)

			val healed = repo.healUnmarkedProjectDirectories()

			assertHealed(lost, healed)
			assertFalse(isMarked(empty))
			assertFalse(isMarked(hidden))
			assertFalse(isMarked(invalid))
			assertTrue(ffs.metadata(dir(oddMarker) / ProjectMetadata.FILENAME).isDirectory)
			assertEquals(project2Id, repo.getProjectId(getProjectDef(PROJECT_2_NAME)))
		}

	@Test
	fun `Heal adopts a folder at its listed path when its name would encode to another one`() =
		scope.runTest {
			val repo = projectsRepository()
			val rawDir = getProjectsDirectory() / "What?"
			ffs.createDirectories(rawDir / SceneDatasource.SCENE_DIRECTORY)

			val healed = repo.healUnmarkedProjectDirectories()

			assertHealed(ProjectDef(testProjectId("What?"), "What?", rawDir.toHPath()), healed)
			assertTrue(ffs.exists(rawDir / ProjectMetadata.FILENAME))
			assertFalse(ffs.exists(repo.getProjectDirectory("What?").toOkioPath()))
		}

	@Test
	fun `Startup heal clears a pre-4 settings baseline that migration moves into place`() = scope.runTest {
		every { settingsRepo.reloadServerSettings() } just runs
		val repo = projectsRepository()
		val lost = createFolder("Old Project", SceneDatasource.SCENE_DIRECTORY)
		ffs.write(dir(lost) / Migration3_4.LEGACY_PROJECT_SETTINGS_FILENAME) {
			writeUtf8("lastSyncedHash = \"stale\"\n\n[data]\nauthorName = \"Pat\"\n")
		}

		migrator(repo).handleDataMigration()

		val settings = readStoredProjectData(lost, ffs, toml)
		assertEquals("Pat", settings.data.authorName)
		assertNull(settings.lastSyncedHash, "a pre-4 baseline is as stale as the journal")
		assertEquals(4, projectsMetaDatasource.loadMetadata(lost).info.dataVersion)
	}

	@Test
	fun `Adopting an empty folder marks it with the given id and no journal`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Server Project")
		val serverId = ProjectId("server-id")

		val adopted = repo.adoptUnmarkedDirectory("Server Project", serverId, serverKnowsProject = false)

		assertEquals(folder.copy(id = serverId), adopted)
		assertEquals(serverId, repo.getProjectId(folder))
		assertEquals(0, projectsMetaDatasource.loadMetadata(folder).info.dataVersion)
		assertFalse(ffs.exists(journalPath(folder)))
	}

	@Test
	fun `Adopting a folder the server knows flags a fresh journal`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Server Project")

		repo.adoptUnmarkedDirectory("Server Project", ProjectId("server-id"), serverKnowsProject = true)

		val journal = readJournal(folder)
		assertTrue(journal.verifyAllEntities)
		assertEquals(emptyMap(), journal.syncedHashes)
		assertEquals(emptyList(), journal.dirty)
	}

	@Test
	fun `Adopting a folder with Hammer content marks it`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Server Project", SceneDatasource.SCENE_DIRECTORY)

		val adopted = repo.adoptUnmarkedDirectory("Server Project", ProjectId("server-id"), serverKnowsProject = true)

		assertEquals(folder.copy(id = ProjectId("server-id")), adopted)
		assertTrue(isMarked(folder))
	}

	@Test
	fun `Adopting a pre-4 folder holding only its legacy settings file marks it`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Server Project")
		ffs.write(dir(folder) / Migration3_4.LEGACY_PROJECT_SETTINGS_FILENAME) { writeUtf8("") }

		val adopted = repo.adoptUnmarkedDirectory("Server Project", ProjectId("server-id"), serverKnowsProject = true)

		assertEquals(folder.copy(id = ProjectId("server-id")), adopted)
	}

	@Test
	fun `Adopting a folder that is not a Hammer project does nothing`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Notes")
		ffs.write(dir(folder) / "shopping.txt") { writeUtf8("eggs") }

		val adopted = repo.adoptUnmarkedDirectory("Notes", ProjectId("server-id"), serverKnowsProject = true)

		assertNull(adopted)
		assertEquals(listOf(dir(folder) / "shopping.txt"), ffs.list(dir(folder)))
	}

	@Test
	fun `Adopting trims the name as creating a project does`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Server Project")

		val adopted = repo.adoptUnmarkedDirectory(" Server Project", ProjectId("server-id"), serverKnowsProject = true)

		assertEquals(folder.copy(id = ProjectId("server-id")), adopted)
		assertFalse(ffs.exists(dir(getProjectDef(" Server Project"))))
	}

	@Test
	fun `Adopting a marked folder does nothing`() = scope.runTest {
		val repo = projectsRepository()
		createProject(ffs, PROJECT_2_NAME)

		val adopted = repo.adoptUnmarkedDirectory(PROJECT_2_NAME, ProjectId("server-id"), serverKnowsProject = true)

		assertNull(adopted)
		assertEquals(project2Id, repo.getProjectId(getProjectDef(PROJECT_2_NAME)))
	}

	@Test
	fun `Adopting a folder whose project toml is a directory does nothing`() = scope.runTest {
		val repo = projectsRepository()
		val folder = createFolder("Odd Marker", ProjectMetadata.FILENAME)

		val adopted = repo.adoptUnmarkedDirectory("Odd Marker", ProjectId("server-id"), serverKnowsProject = true)

		assertNull(adopted)
		assertTrue(ffs.metadata(dir(folder) / ProjectMetadata.FILENAME).isDirectory)
		assertFalse(ffs.exists(journalPath(folder)))
	}

	@Test
	fun `Adopting a missing folder does nothing`() = scope.runTest {
		val repo = projectsRepository()

		val adopted = repo.adoptUnmarkedDirectory("Not There", ProjectId("server-id"), serverKnowsProject = true)

		assertNull(adopted)
		assertFalse(ffs.exists(dir(getProjectDef("Not There"))))
	}
}
