package repositories

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProjectDirectories
import createRootDirectory
import dev.icerock.moko.resources.StringResource
import getProjectsDirectory
import io.mockk.coEvery
import io.mockk.coJustAwait
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import projectNames
import utils.BaseTest
import kotlin.test.*

class ProjectsRepositoryTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var settingsRepo: GlobalSettingsRepository
	private lateinit var projectsMetaRepo: ProjectMetadataRepository
	private lateinit var settings: GlobalSettings
	private lateinit var toml: Toml

	@Before
	override fun setup() {
		super.setup()
		toml = createTomlSerializer()

		projectsMetaRepo = mockk(relaxed = true)
		every { projectsMetaRepo.loadMetadata(any()) } returns
			ProjectMetadata(
				info = Info(
					created = Instant.DISTANT_FUTURE,
					lastAccessed = Instant.DISTANT_FUTURE,
				)
			)

		settingsRepo = mockk()
		settings = mockk()
		every { settingsRepo.globalSettings } answers { settings }
		coEvery { settingsRepo.globalSettingsUpdates } coAnswers {
			val flow = mockk<SharedFlow<GlobalSettings>>()
			coJustAwait { flow.collect(any()) }
			flow
		}
		every { settings.projectsDirectory } answers { getProjectsDirectory().toString() }

		ffs = FakeFileSystem()

		createRootDirectory(ffs)
		setupKoin()
	}

	@After
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	@Test
	fun `ProjectsRepository init`() = scope.runTest {
		val projDir = getProjectsDirectory()
		assertFalse(ffs.exists(projDir), "Dir should not have existed already")
		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)
		assertTrue(ffs.exists(projDir), "Init did not create project dir")
	}

	private fun assertFailure(filename: String?, error: StringResource) {
		val result = ProjectsRepository.validateFileName(filename)
		assert(result.isFailure)

		val exception = result.exceptionOrNull() as? ValidationFailedException
		assertNotNull(exception)

		assertEquals(error, exception.errorMessage)
	}

	@Test
	fun `Scene Name Validation`() = scope.runTest {
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
		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)
		val projectDir = repo.getProjectsDirectory()
		assertEquals(actualProjDir, projectDir)
	}

	@Test
	fun `Get Projects`() = scope.runTest {
		createProjectDirectories(ffs)

		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)
		val projects = repo.getProjects()

		assertEquals(projectNames.size, projects.size)
		projects.forEach { project ->
			projectNames.contains(project.name)
		}
	}

	@Test
	fun `Get Project Directory`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)

		val projectName = projectNames[0]
		val projectDir = repo.getProjectDirectory(projectName)

		val actualProjDir = getProjectsDirectory().div(projectName)
		assertEquals(actualProjDir, projectDir.toOkioPath())
	}

	@Test
	fun `Create Project`() = scope.runTest {
		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)

		val projectName = projectNames[0]
		val result = repo.createProject(projectName)
		assertTrue(result.isSuccess)

		val actualProjDir = getProjectsDirectory().div(projectName)
		assertTrue(ffs.exists(actualProjDir))

		val result2 = repo.createProject(projectName)
		assertFalse(result2.isSuccess)
	}

	@Test
	fun `Delete Project`() = scope.runTest {
		createProjectDirectories(ffs)
		val repo = ProjectsRepositoryOkio(ffs, settingsRepo, projectsMetaRepo)

		val projectName = projectNames[0]
		val projPath = getProjectsDirectory().div(projectName)
		val projDef = ProjectDefinition(projectName, projPath.toHPath())

		val deleted = repo.deleteProject(projDef)
		assertTrue(deleted)

		assertFalse(ffs.exists(projPath))

		val deleteAgain = repo.deleteProject(projDef)
		assertFalse(deleteAgain)
	}
}