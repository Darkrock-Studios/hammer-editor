package repositories.projectsrepository

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import okio.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProjectsRepositoryIdTest : ProjectsRepositoryBaseTest() {
	private val project2Id = ProjectId("8c3f6a2e-4b1d-4e5a-9f7c-2d6b1a0e9c43")

	@Test
	fun `Get Project ID`() = scope.runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = projectsRepository()

		assertEquals(project2Id, repo.getProjectId(getProjectDef(PROJECT_2_NAME)))
	}

	@Test
	fun `Get Project ID heals a project without one`() = scope.runTest {
		val proj1Def = getProjectDef(PROJECT_1_NAME)
		// Test Project 1 predates project ids (it only has the legacy serverProjectId).
		createProject(ffs, PROJECT_1_NAME)

		val repo = projectsRepository()
		val healed = repo.getProjectId(proj1Def)

		assertEquals(healed, repo.getProjectId(proj1Def))
		assertEquals(healed, projectsMetaDatasource.loadMetadata(proj1Def).info.projectId)
	}

	@Test
	fun `Set Project ID adopts a different identity`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val projectId = ProjectId("test-id")
		createProject(ffs, PROJECT_2_NAME)

		val repo = projectsRepository()
		repo.setProjectId(proj2Def, projectId)

		assertEquals(projectId, repo.getProjectId(proj2Def))
		assertEquals(proj2Def.name, repo.findProject(projectId)?.name)
	}

	@Test
	fun `Create Project mints an id`() = scope.runTest {
		val repo = projectsRepository()

		val first = repo.createProject("First", seedDefaultLanguage = false)
		val second = repo.createProject("Second", seedDefaultLanguage = false)

		assertTrue(isSuccess(first) && isSuccess(second))
		assertNotEquals(repo.getProjectId(first.data), repo.getProjectId(second.data))
	}

	@Test
	fun `Create Project uses a supplied id`() = scope.runTest {
		val repo = projectsRepository()
		val projectId = ProjectId("from-the-server")

		val result = repo.createProject("From Server", seedDefaultLanguage = false, projectId = projectId)

		assertTrue(isSuccess(result))
		assertEquals(projectId, repo.getProjectId(result.data))
	}

	@Test
	fun `A copied project folder is given a new id`() = scope.runTest {
		createProject(ffs, PROJECT_2_NAME)
		val original = getProjectDef(PROJECT_2_NAME)
		val copy = copyProject(original, "Test Project 2 copy")
		// Only the original has synced, so it is the one the server knows by this id.
		ffs.write(original.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME) { writeUtf8("{}") }

		val repo = projectsRepository()
		repo.resolveDuplicateProjectIds()

		assertEquals(project2Id, repo.getProjectId(original))
		assertNotEquals(project2Id, repo.getProjectId(copy))
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

		assertEquals(project2Id, repo.getProjectId(original))
		assertNotEquals(project2Id, repo.getProjectId(copy))
	}

	@Test
	fun `Distinct ids are left alone`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val project1Id = repo.getProjectId(getProjectDef(PROJECT_1_NAME))

		repo.resolveDuplicateProjectIds()

		assertEquals(project1Id, repo.getProjectId(getProjectDef(PROJECT_1_NAME)))
		assertEquals(project2Id, repo.getProjectId(getProjectDef(PROJECT_2_NAME)))
	}

	private fun copyProject(source: ProjectDef, name: String): ProjectDef {
		val target: Path = getProjectsDirectory() / name
		ffs.createDirectories(target)
		ffs.list(source.path.toOkioPath()).forEach { file ->
			if (ffs.metadata(file).isRegularFile) ffs.copy(file, target / file.name)
		}
		return ProjectDef(name, target.toHPath())
	}
}
