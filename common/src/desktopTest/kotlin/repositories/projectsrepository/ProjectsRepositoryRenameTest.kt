package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectRenameFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import createProjectDirectories
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import projectNames
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectsRepositoryRenameTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `Rename Project, success`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()

		val projectName = projectNames[0]
		val newProjectName = "New Project Name"
		val projPath = getProjectsDirectory().div(projectName)
		val projDef = ProjectDefinition(testProjectId(projectName), projectName, projPath.toHPath())

		val newProjPath = getProjectsDirectory().div(newProjectName)

		val result = repo.renameProject(projDef, newProjectName)
		assertTrue(isSuccess(result))
		assertEquals(ProjectDef(projDef.id, newProjectName, newProjPath.toHPath()), result.data)
		assertTrue(ffs.exists(newProjPath))
	}

	@Test
	fun `Rename Project keeps its id`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()
		val original = repo.findProject(projectNames[0])!!

		val renamed = repo.renameProject(original, "New Project Name")

		assertTrue(isSuccess(renamed))
		assertEquals(original.id, renamed.data.id)
		assertEquals(renamed.data, repo.findProject(original.id))
	}

	@Test
	fun `Rename Project, invalid name`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()

		val projectName = projectNames[0]
		val newProjectName = "@invalid Project Name"
		val projPath = getProjectsDirectory().div(projectName)
		val projDef = ProjectDefinition(testProjectId(projectName), projectName, projPath.toHPath())

		val oldProjPath = getProjectsDirectory().div(projectName)
		val newProjPath = getProjectsDirectory().div(newProjectName)

		val result = repo.renameProject(projDef, newProjectName)
		assertTrue(isFailure(result))

		assertIs<ProjectRenameFailed>(result.exception)
		assertEquals(
			ProjectRenameFailed.Reason.InvalidName,
			(result.exception as ProjectRenameFailed).reason
		)
		assertTrue(ffs.exists(oldProjPath))
		assertFalse(ffs.exists(newProjPath))
	}

	@Test
	fun `Rename Project, project does not exist`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()

		val projectName = "None Existent Project"
		val newProjectName = "New Project Name"
		val projPath = getProjectsDirectory() / projectName
		val projDef = ProjectDefinition(testProjectId(projectName), projectName, projPath.toHPath())

		val oldProjPath = getProjectsDirectory().div(projectName)
		val newProjPath = getProjectsDirectory().div(newProjectName)

		val result = repo.renameProject(projDef, newProjectName)
		assertTrue(isFailure(result))

		assertIs<ProjectRenameFailed>(result.exception)
		assertEquals(
			ProjectRenameFailed.Reason.SourceDoesNotExist,
			(result.exception as ProjectRenameFailed).reason
		)
		assertFalse(ffs.exists(oldProjPath))
		assertFalse(ffs.exists(newProjPath))
	}

	@Test
	fun `Rename Project, already exists`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()

		val projectName = projectNames[0]
		val newProjectName = projectNames[1]
		val projPath = getProjectsDirectory().div(projectName)
		val projDef = ProjectDefinition(testProjectId(projectName), projectName, projPath.toHPath())

		val oldProjPath = getProjectsDirectory().div(projectName)
		val newProjPath = getProjectsDirectory().div(newProjectName)

		val result = repo.renameProject(projDef, newProjectName)
		assertTrue(isFailure(result))

		assertIs<ProjectRenameFailed>(result.exception)
		assertEquals(
			ProjectRenameFailed.Reason.AlreadyExists,
			(result.exception as ProjectRenameFailed).reason
		)
		assertTrue(ffs.exists(oldProjPath))
		assertTrue(ffs.exists(newProjPath))
	}

	@Test
	fun `Rename Project, project in use`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = projectsRepository()

		val projectName = projectNames[0]
		val newProjectName = "New Project Name"
		val projDef = ProjectDefinition(testProjectId(projectName), projectName, getProjectsDirectory().div(projectName).toHPath())
		val projectScope = createProjectScope(projDef)

		val result = try {
			repo.renameProject(projDef, newProjectName)
		} finally {
			projectScope.close()
		}

		assertTrue(isFailure(result))
		assertEquals(
			ProjectRenameFailed.Reason.ProjectInUse,
			(result.exception as ProjectRenameFailed).reason
		)
		assertTrue(ffs.exists(getProjectsDirectory().div(projectName)))
		assertFalse(ffs.exists(getProjectsDirectory().div(newProjectName)))
	}
}
