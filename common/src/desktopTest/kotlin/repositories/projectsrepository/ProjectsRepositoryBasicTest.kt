package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectCreationFailedException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProjectDirectories
import dev.icerock.moko.resources.StringResource
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import projectNames
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectsRepositoryBasicTest : ProjectsRepositoryBaseTest() {

	@Test
	fun `ProjectsRepository init`() = scope.runTest {
		val projDir = getProjectsDirectory()
		assertFalse(ffs.exists(projDir), "Dir should not have existed already")
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)
		assertTrue(ffs.exists(projDir), "Init did not create project dir")
	}

	@Test
	fun `File Name Validation`() = scope.runTest {
		listOf("good", "cliché", "one two", "one_two", "1234567890", "nums1234567890", "aZ").forEach {
			assertTrue(ProjectsRepository.validateFileName(it).isSuccess)
		}

		assertFailure(null, MR.strings.create_project_error_null_filename)
		assertFailure("", MR.strings.create_project_error_blank)
		assertFailure("   ", MR.strings.create_project_error_blank)

		listOf("bad*bad", "bad-bad", "bad/bad", """bad\bad""").forEach {
			assertFailure(it, MR.strings.create_project_error_invalid_characters)
		}
	}

	@Test
	fun `Get Projects Directory`() = scope.runTest {
		val actualProjDir = getProjectsDirectory().toHPath()
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)
		val projectDir = repo.getProjectsDirectory()
		assertEquals(actualProjDir, projectDir)
	}

	@Test
	fun `Ensure Projects Directory`() = scope.runTest {
		val actualProjDir = getProjectsDirectory()
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		ffs.deleteRecursively(actualProjDir)
		assertFalse(ffs.exists(actualProjDir))
		repo.ensureProjectDirectory()
		assertTrue(ffs.exists(actualProjDir))
	}

	@Test
	fun `Get Projects`() = scope.runTest {
		createProjectDirectories(ffs)

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)
		val projects = repo.getProjects()

		assertEquals(projectNames.size, projects.size)
		projects.forEach { project ->
			assertTrue(projectNames.contains(project.name))
		}
	}

	@Test
	fun `Get Project Directory`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectName = projectNames[0]
		val projectDir = repo.getProjectDirectory(projectName)

		val actualProjDir = getProjectsDirectory().div(projectName)
		assertEquals(actualProjDir, projectDir.toOkioPath())
	}

	@Test
	fun `Create Project`() = scope.runTest {
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectName = projectNames[0]
		val result = repo.createProject(projectName)
		assertTrue(result.isSuccess)

		val actualProjDir = getProjectsDirectory().div(projectName)
		assertTrue(ffs.exists(actualProjDir))

		val result2 = repo.createProject(projectName)
		assertFalse(result2.isSuccess)

		val newDef = ProjectDef(projectName, actualProjDir.toHPath())
		val metadataDatasource = ProjectMetadataDatasource(ffs, toml)
		val metadataPath = metadataDatasource.getMetadataPath(newDef)
		ffs.exists(metadataPath.toOkioPath())
	}

	@Test
	fun `Create Project failure with invalid name`() = scope.runTest {
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectName = "!@/Invalid Name"
		val result = repo.createProject(projectName)
		assertTrue(isFailure(result))
		assertTrue(result.exception is ProjectCreationFailedException)
	}

	@Test
	fun `Delete Project`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectName = projectNames[0]
		val projPath = getProjectsDirectory().div(projectName)
		val projDef = ProjectDefinition(projectName, projPath.toHPath())

		val deleted = repo.deleteProject(projDef)
		assertTrue(deleted)

		assertFalse(ffs.exists(projPath))

		val deleteAgain = repo.deleteProject(projDef)
		assertFalse(deleteAgain)
	}

	private fun assertFailure(filename: String?, error: StringResource) {
		val result = ProjectsRepository.validateFileName(filename)

		assertTrue(isFailure(result))
		val exception = result.exception as? ValidationFailedException
		assertNotNull(exception)

		assertEquals(error, exception.errorMessage)
	}
}