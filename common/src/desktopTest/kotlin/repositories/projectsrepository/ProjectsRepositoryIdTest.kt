package repositories.projectsrepository

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectIdChangeListener
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectInUseException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import listedProjectId
import okio.FileMetadata
import okio.ForwardingFileSystem
import okio.Path
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProjectsRepositoryIdTest : ProjectsRepositoryBaseTest() {
	private val project2Id = testProjectId(PROJECT_2_NAME)

	@Test
	fun `Get Projects reads each project's id from disk`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)

		val projects = projectsRepository().getProjects()

		assertEquals(
			mapOf(
				PROJECT_1_NAME to ProjectId("5f1d7446-1f08-4909-a81e-cdc7470a2f63"),
				PROJECT_2_NAME to ProjectId("8c3f6a2e-4b1d-4e5a-9f7c-2d6b1a0e9c43"),
			),
			projects.associate { it.name to it.id },
		)
	}

	@Test
	fun `Get Projects heals a project without an id`() = scope.runTest {
		val proj1Def = getProjectDef(PROJECT_1_NAME)
		createProject(ffs, PROJECT_1_NAME)
		ffs.write(proj1Def.path.toOkioPath() / ProjectMetadata.FILENAME) {
			writeUtf8("[info]\ncreated = 2022-12-30T07:08:02.691261600Z\ndataVersion = 4\n")
		}
		val repo = projectsRepository()

		val healed = repo.getProjects().single()

		assertEquals(healed.id, projectsMetaDatasource.loadMetadata(proj1Def).info.projectId)
		assertEquals(listOf(healed), repo.getProjects())
	}

	@Test
	fun `Get Projects skips a project deleted while the list is built`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val vanishingMarker = getProjectDef(PROJECT_1_NAME).path.toOkioPath() / ProjectMetadata.FILENAME
		// Deletes the project right after the listing sees its marker, before its id is read.
		val fs = object : ForwardingFileSystem(ffs) {
			override fun metadataOrNull(path: Path): FileMetadata? =
				super.metadataOrNull(path).also { if (path == vanishingMarker) ffs.deleteRecursively(path.parent!!) }
		}
		val repo = ProjectsRepository(
			fs, settingsRepo, ProjectMetadataDatasource(fs, toml), GroupMetadataDatasource(fs, toml), toml, json,
			deviceLocaleResolver,
		)

		assertEquals(listOf(PROJECT_2_NAME), repo.getProjects().map { it.name })
	}

	@Test
	fun `Set Project ID returns the def under its new id`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val newId = ProjectId.randomUUID()
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()

		val updated = repo.setProjectId(proj2Def, newId)

		assertEquals(proj2Def.copy(id = newId), updated)
		assertEquals(updated, repo.findProject(newId))
	}

	@Test
	fun `Set Project ID adopts a different identity`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val projectId = ProjectId("test-id")
		createProject(ffs, PROJECT_2_NAME)

		val repo = projectsRepository()
		repo.setProjectId(proj2Def, projectId)

		assertEquals(projectId, repo.listedProjectId(proj2Def))
		assertEquals(proj2Def.name, repo.findProject(projectId)?.name)
	}

	@Test
	fun `Set Project ID refuses a project in use`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val projectScope = createProjectScope(proj2Def)

		try {
			assertFailsWith<ProjectInUseException> { repo.setProjectId(proj2Def, ProjectId("test-id")) }
		} finally {
			projectScope.close()
		}

		assertEquals(project2Id, repo.listedProjectId(proj2Def))
	}

	@Test
	fun `A project is in use under any def for its path while its scope is open`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val reidentified = proj2Def.copy(id = ProjectId.randomUUID())
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val projectScope = createProjectScope(proj2Def)

		try {
			assertTrue(repo.isProjectInUse(reidentified))
			assertTrue(repo.isProjectInUse(proj2Def.path))
		} finally {
			projectScope.close()
		}

		assertFalse(repo.isProjectInUse(proj2Def))
	}

	@Test
	fun `Set Project ID tells listeners the old and new id once it is stored`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val newId = ProjectId("test-id")
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val changes = mutableListOf<Triple<String, ProjectId, ProjectId>>()
		var storedWhenNotified: ProjectId? = null
		repo.addProjectIdChangeListener { def, oldId, id ->
			storedWhenNotified = repo.listedProjectId(def)
			changes += Triple(def.name, oldId, id)
		}

		repo.setProjectId(proj2Def, newId)

		assertEquals(listOf(Triple(proj2Def.name, project2Id, newId)), changes)
		assertEquals(newId, storedWhenNotified)
	}

	@Test
	fun `Set Project ID to the id it already has notifies nobody`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		var notified = false
		repo.addProjectIdChangeListener { _, _, _ -> notified = true }

		repo.setProjectId(proj2Def, project2Id)

		assertFalse(notified)
	}

	@Test
	fun `A failing listener does not fail the id change`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val newId = ProjectId("test-id")
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		var laterListenerRan = false
		repo.addProjectIdChangeListener { _, _, _ -> error("listener broke") }
		repo.addProjectIdChangeListener { _, _, _ -> laterListenerRan = true }

		repo.setProjectId(proj2Def, newId)

		assertEquals(newId, repo.listedProjectId(proj2Def))
		assertTrue(laterListenerRan)
	}

	@Test
	fun `Create Project mints an id`() = scope.runTest {
		val repo = projectsRepository()

		val first = repo.createProject("First", seedDefaultLanguage = false)
		val second = repo.createProject("Second", seedDefaultLanguage = false)

		assertTrue(isSuccess(first) && isSuccess(second))
		assertNotEquals(repo.listedProjectId(first.data), repo.listedProjectId(second.data))
	}

	@Test
	fun `Create Project uses a supplied id`() = scope.runTest {
		val repo = projectsRepository()
		val projectId = ProjectId("from-the-server")

		val result = repo.createProject("From Server", seedDefaultLanguage = false, projectId = projectId)

		assertTrue(isSuccess(result))
		assertEquals(projectId, repo.listedProjectId(result.data))
	}

	@Test
	fun `A copied project folder is given a new id`() = scope.runTest {
		createProject(ffs, PROJECT_2_NAME)
		val original = getProjectDef(PROJECT_2_NAME)
		val copy = copyProject(original, "Test Project 2 copy")
		// Only the original has synced, so it is the one the server knows by this id.
		ffs.write(original.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME) { writeUtf8("{}") }

		val repo = projectsRepository()
		repo.resolveDuplicateProjectIds()

		assertEquals(project2Id, repo.listedProjectId(original))
		assertNotEquals(project2Id, repo.listedProjectId(copy))
	}

	@Test
	fun `Without a sync journal the earliest created project keeps a shared id`() = scope.runTest {
		createProject(ffs, PROJECT_2_NAME)
		val original = getProjectDef(PROJECT_2_NAME)
		val copy = copyProject(original, "A later copy")
		projectsMetaDatasource.updateMetadata(copy) {
			it.copy(info = it.info.copy(created = it.info.created + 1.days))
		}

		val repo = projectsRepository()
		repo.resolveDuplicateProjectIds()

		assertEquals(project2Id, repo.listedProjectId(original))
		assertNotEquals(project2Id, repo.listedProjectId(copy))
	}

	@Test
	fun `A copied project folder in use keeps the shared id until it is closed`() = scope.runTest {
		createProject(ffs, PROJECT_2_NAME)
		val original = getProjectDef(PROJECT_2_NAME)
		val copy = copyProject(original, "Test Project 2 copy")
		ffs.write(original.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME) { writeUtf8("{}") }
		val repo = projectsRepository()
		val projectScope = createProjectScope(copy)

		try {
			repo.resolveDuplicateProjectIds()
		} finally {
			projectScope.close()
		}

		assertEquals(project2Id, repo.listedProjectId(original))
		assertEquals(project2Id, repo.listedProjectId(copy))
	}

	@Test
	fun `Distinct ids are left alone`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val project1Id = repo.listedProjectId(getProjectDef(PROJECT_1_NAME))

		repo.resolveDuplicateProjectIds()

		assertEquals(project1Id, repo.listedProjectId(getProjectDef(PROJECT_1_NAME)))
		assertEquals(project2Id, repo.listedProjectId(getProjectDef(PROJECT_2_NAME)))
	}

	private fun copyProject(source: ProjectDef, name: String): ProjectDef {
		val target: Path = getProjectsDirectory() / name
		ffs.createDirectories(target)
		ffs.list(source.path.toOkioPath()).forEach { file ->
			if (ffs.metadata(file).isRegularFile) ffs.copy(file, target / file.name)
		}
		return ProjectDef(testProjectId(name), name, target.toHPath())
	}
}
