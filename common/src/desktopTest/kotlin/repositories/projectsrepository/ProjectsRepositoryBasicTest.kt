package repositories.projectsrepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectCreationFailedException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.create_project_error_blank
import com.darkrockstudios.apps.hammer.create_project_error_invalid_characters
import com.darkrockstudios.apps.hammer.create_project_error_null_filename
import createProjectDirectories
import getProjectsDirectory
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
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
		// Original allowed set
		listOf("good", "cliché", "one two", "one_two", "1234567890", "nums1234567890", "aZ").forEach {
			assertTrue(ProjectsRepository.validateFileName(it).isSuccess, "expected success for: $it")
		}

		// Newly allowed: hyphens, punctuation, parens, OS-forbidden chars (encoded on disk),
		// and typographic quotes.
		listOf(
			"It's-a-me",
			"Chapter 3: The Fall",
			"What?",
			"A & B",
			"Foo (Bar) Baz",
			"Hello, World!",
			"Wait--really!",
			"A/B Testing",
			"path\\with\\backslash",
			"star*name",
			"quote \"thing\"",
			"angle <bracket>",
			"pipe|sep",
			"It’s curly",
			"“Hello”",
		).forEach {
			assertTrue(ProjectsRepository.validateFileName(it).isSuccess, "expected success for: $it")
		}

		assertFailure(null, Res.string.create_project_error_null_filename)
		assertFailure("", Res.string.create_project_error_blank)
		assertFailure("   ", Res.string.create_project_error_blank)

		// Newly disallowed: tilde (reserved delimiter), Windows reserved names, trailing
		// dot/space, leading dot, and chars still outside the allowed set (e.g. @, #, $, %).
		listOf(
			"bad~bad",
			"~leading",
			"trailing~",
			"CON",
			"con",
			"PRN.txt",
			"COM1",
			"name.",
			"name ",
			".hidden",
			"bad@bad",
			"bad#bad",
			"bad\$bad",
			"bad%bad",
		).forEach {
			assertFailure(it, Res.string.create_project_error_invalid_characters)
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
	fun `Get Projects ignores non-project directories`() = scope.runTest {
		createProjectDirectories(ffs)
		val projDir = getProjectsDirectory()

		// Directory without a project.toml
		ffs.createDirectory(projDir.div("Not A Project"))
		// Hidden directory
		ffs.createDirectory(projDir.div(".hidden"))
		ffs.write(projDir.div(".hidden").div(ProjectMetadata.FILENAME)) { writeUtf8("") }
		// project.toml exists but is a directory, not a file
		ffs.createDirectories(projDir.div("Sneaky").div(ProjectMetadata.FILENAME))
		// project.toml present, but the folder is not a valid project name
		ffs.createDirectory(projDir.div("bad~name"))
		ffs.write(projDir.div("bad~name").div(ProjectMetadata.FILENAME)) { writeUtf8("") }
		// Stray file at the top level
		ffs.write(projDir.div("stray.txt")) { writeUtf8("") }

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)
		val projects = repo.getProjects()

		assertEquals(projectNames.toSet(), projects.map { it.name }.toSet())
	}

	@Test
	fun `Get Projects decodes encoded folder names`() = scope.runTest {
		val projDir = getProjectsDirectory()
		val encodedName = ProjectsRepository.encodeForFilename("Chapter 3: The Fall?")
		ffs.createDirectories(projDir.div(encodedName))
		ffs.write(projDir.div(encodedName).div(ProjectMetadata.FILENAME)) { writeUtf8("") }

		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)
		val projects = repo.getProjects()

		assertEquals(listOf("Chapter 3: The Fall?"), projects.map { it.name })
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
		assertTrue(
			ffs.exists(metadataPath.toOkioPath()),
			"createProject must write ${ProjectMetadata.FILENAME} immediately"
		)
	}

	@Test
	fun `Create Project failure with invalid name`() = scope.runTest {
		val repo = ProjectsRepository(ffs, settingsRepo, projectsMetaDatasource)

		val projectName = "!@/Invalid Name"
		val result = repo.createProject(projectName)
		assertTrue(isFailure(result))
		assertTrue(result.exception is ProjectCreationFailedException)
		assertTrue(
			ffs.list(getProjectsDirectory()).isEmpty(),
			"no project directory may be created for an invalid name"
		)
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