package repositories.projectsrepository

import OUT_OF_ORDER_PROJECT_NAME
import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectsRepositoryFindTest : ProjectsRepositoryBaseTest() {
	@Test
	fun `Find Project By name, success`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectDef = repo.findProject(PROJECT_2_NAME)

		assertEquals(proj2Def, projectDef)
	}

	@Test
	fun `Find Project By name, failure`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectDef = repo.findProject(PROJECT_2_NAME)

		assertNull(projectDef)
	}

	@Test
	fun `Find Project By id, success`() = scope.runTest {
		val proj1Id = ProjectId("5f1d7446-1f08-4909-a81e-cdc7470a2f63")
		val proj1Def = getProjectDef(PROJECT_1_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectDef = repo.findProject(proj1Id)

		assertEquals(proj1Def, projectDef)
	}

	@Test
	fun `Find Project By id, not found`() = scope.runTest {
		val fakeId = ProjectId("fake-id")

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectDef = repo.findProject(fakeId)

		assertNull(projectDef)
	}

	@Test
	fun `Get Project By name, success`() = scope.runTest {
		val proj1Def = getProjectDef(PROJECT_1_NAME)
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		createProject(ffs, OUT_OF_ORDER_PROJECT_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectDef = repo.getProjectDefinition(PROJECT_1_NAME)

		assertEquals(proj1Def, projectDef)
	}
}