package repositories.projectsrepository

import OUT_OF_ORDER_PROJECT_NAME
import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import createProject
import getProjectsDirectory
import getProjectDef
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsRepositoryFindTest : ProjectsRepositoryBaseTest() {
	@Test
	fun `Find Project By name, success`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = projectsRepository()

		val projectDef = repo.findProject(PROJECT_2_NAME)

		assertEquals(proj2Def, projectDef)
	}

	@Test
	fun `Find Project By name, failure`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = projectsRepository()

		val projectDef = repo.findProject(PROJECT_2_NAME)

		assertNull(projectDef)
	}

	@Test
	fun `Find Project By id, success`() = scope.runTest {
		val proj2Id = testProjectId(PROJECT_2_NAME)
		val proj2Def = getProjectDef(PROJECT_2_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = projectsRepository()

		val projectDef = repo.findProject(proj2Id)

		assertEquals(proj2Def, projectDef)
	}

	@Test
	fun `Find Project By id, not found`() = scope.runTest {
		val fakeId = ProjectId("fake-id")

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = projectsRepository()

		val projectDef = repo.findProject(fakeId)

		assertNull(projectDef)
	}

	// Guards the name-only intent resolution used by ProjectRootActivity: a name that does not
	// match an on-disk project must resolve to nothing, so a forged intent cannot open an
	// arbitrary project.
	@Test
	fun `Resolve project by name against on-disk projects only`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)

		val repo = projectsRepository()

		val known = repo.getProjects().firstOrNull { it.name == PROJECT_1_NAME }
		val forged = repo.getProjects().firstOrNull { it.name == "../../attacker/controlled" }

		assertEquals(getProjectDef(PROJECT_1_NAME), known)
		assertNull(forged)
	}

	@Test
	fun `Only project, only while nothing else is beside it`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()

		val alone = repo.isOnlyProject(PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val withAnother = repo.isOnlyProject(PROJECT_1_NAME)

		assertTrue(alone)
		assertFalse(withAnother)
	}

	@Test
	fun `Not the only project when it is not there at all`() = scope.runTest {
		ffs.createDirectories(getProjectsDirectory())
		val repo = projectsRepository()

		val missing = repo.isOnlyProject(PROJECT_1_NAME)

		assertFalse(missing)
	}

	@Test
	fun `A folder of project files without its project file still counts as another project`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		ffs.createDirectories(getProjectsDirectory() / "Old Novel" / SceneDatasource.SCENE_DIRECTORY)
		val repo = projectsRepository()

		val alone = repo.isOnlyProject(PROJECT_1_NAME)

		assertFalse(alone)
	}
}
