package repositories.projectsrepository

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import createProject
import getProjectDef
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals

class ProjectPluginsFollowProjectsTest : ProjectsRepositoryBaseTest() {

	private fun stored() = ProjectPluginsDatasource(ffs, toml).load()

	private fun following(repo: ProjectsRepository) =
		ProjectPluginsRepository(ProjectPluginsDatasource(ffs, toml)).also { it.followProjectChanges(repo) }

	@Test
	fun `deleting a project forgets the plugins it turned off`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val plugins = following(repo)
		plugins.setPluginEnabled(testProjectId(PROJECT_1_NAME), "style", false)
		plugins.setPluginEnabled(testProjectId(PROJECT_2_NAME), "style", false)

		repo.deleteProject(getProjectDef(PROJECT_1_NAME))
		advanceUntilIdle()

		assertEquals(mapOf(testProjectId(PROJECT_2_NAME) to setOf("style")), stored())
	}

	@Test
	fun `a project's new id keeps the plugins it turned off`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val plugins = following(repo)
		plugins.setPluginEnabled(testProjectId(PROJECT_1_NAME), "style", false)

		repo.setProjectId(getProjectDef(PROJECT_1_NAME), ProjectId("new-id"))

		assertEquals(mapOf(ProjectId("new-id") to setOf("style")), stored())
	}

	@Test
	fun `a copy given a new id starts with the original's choices, and the original keeps them`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		createProject(ffs, PROJECT_2_NAME)
		val repo = projectsRepository()
		val plugins = following(repo)
		val originalId = testProjectId(PROJECT_1_NAME)
		repo.setProjectId(getProjectDef(PROJECT_2_NAME), originalId)
		plugins.setPluginEnabled(originalId, "style", false)

		repo.setProjectId(getProjectDef(PROJECT_2_NAME).copy(id = originalId), ProjectId("copy-id"))

		assertEquals(mapOf(originalId to setOf("style"), ProjectId("copy-id") to setOf("style")), stored())
	}
}
