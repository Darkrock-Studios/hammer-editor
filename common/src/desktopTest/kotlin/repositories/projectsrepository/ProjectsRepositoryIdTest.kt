package repositories.projectsrepository

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
import kotlin.test.assertNotNull

class ProjectsRepositoryIdTest : ProjectsRepositoryBaseTest() {
	@Test
	fun `Get Project ID`() = scope.runTest {
		val proj1Def = getProjectDef(PROJECT_1_NAME)
		val proj2Def = getProjectDef(PROJECT_2_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		assertEquals(ProjectId("5f1d7446-1f08-4909-a81e-cdc7470a2f63"), repo.getProjectId(proj1Def))
		assertNull(repo.getProjectId(proj2Def))
	}

	@Test
	fun `Set Project ID`() = scope.runTest {
		val proj2Def = getProjectDef(PROJECT_2_NAME)
		val projectId = ProjectId("test-id")

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		assertNull(repo.getProjectId(proj2Def))

		repo.setProjectId(proj2Def, projectId)

		val loadedId = repo.getProjectId(proj2Def)
		assertEquals(projectId, loadedId)
	}

	@Test
	fun `Remove Project ID`() = scope.runTest {
		val proj1Def = getProjectDef(PROJECT_1_NAME)

		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		assertNotNull(repo.getProjectId(proj1Def))

		repo.removeProjectId(proj1Def)

		val loadedId = repo.getProjectId(proj1Def)
		assertNull(loadedId)
	}

}