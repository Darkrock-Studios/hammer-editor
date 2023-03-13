package components.projectselection

import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import getProjectsDirectory
import io.mockk.*
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals

class ProjectSelectionComponentTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>

	lateinit var globalSettingsRepository: GlobalSettingsRepository
	lateinit var projectsRepository: ProjectsRepository
	lateinit var exampleProjectRepository: ExampleProjectRepository

	@Before
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()

		context = mockk()
		lifecycle = mockk()
		lifecycleCallbacks = mutableListOf()

		globalSettingsRepository = mockk()
		projectsRepository = mockk()
		exampleProjectRepository = mockk()

		val testModule = module {
			single { globalSettingsRepository } bind GlobalSettingsRepository::class
			single { projectsRepository } bind ProjectsRepository::class
			single { exampleProjectRepository } bind ExampleProjectRepository::class
		}
		setupKoin(testModule)

		every { context.lifecycle } returns lifecycle
		every {
			lifecycle.subscribe(capture(lifecycleCallbacks))
		} just Runs

		every { lifecycle.unsubscribe(any()) } just Runs

		val projectsDir = getProjectsDirectory()
		every { projectsRepository.getProjectsDirectory() } returns projectsDir.toHPath()
		ffs.createDirectories(projectsDir)

		val globalSettings = GlobalSettings(
			projectsDirectory = projectsDir.toStr(),
		)
		every { globalSettingsRepository.globalSettings } returns globalSettings
	}

	@Test
	fun `Initialize ProjectSelectionComponent - No Example project`() {
		every { exampleProjectRepository.shouldInstallFirstTime() } returns false

		val component = ProjectSelectionComponent(
			componentContext = context,
			showProjectDirectory = false,
			onProjectSelected = {},
		)

		verify(exactly = 0) { exampleProjectRepository.install() }
	}

	@Test
	fun `Initialize ProjectSelectionComponent - Install Example project`() {
		every { exampleProjectRepository.shouldInstallFirstTime() } returns true
		every { exampleProjectRepository.install() } just Runs

		val component = ProjectSelectionComponent(
			componentContext = context,
			showProjectDirectory = false,
			onProjectSelected = {},
		)

		verify(exactly = 1) { exampleProjectRepository.install() }
	}

	@Test
	fun `Create Project - Fail`() {
		every { exampleProjectRepository.shouldInstallFirstTime() } returns false

		val projectNameSlot = slot<String>()
		every { projectsRepository.createProject(capture(projectNameSlot)) } returns false

		val component = ProjectSelectionComponent(
			componentContext = context,
			showProjectDirectory = false,
			onProjectSelected = {},
		)

		val testProjectName = "Test Project"
		component.createProject(testProjectName)

		assertEquals(testProjectName, projectNameSlot.captured)
	}

	@Test
	fun `Create Project - Succeed`() {
		every { exampleProjectRepository.shouldInstallFirstTime() } returns false

		val projectNameSlot = slot<String>()
		every { projectsRepository.createProject(capture(projectNameSlot)) } returns true

		val component = ProjectSelectionComponent(
			componentContext = context,
			showProjectDirectory = false,
			onProjectSelected = {},
		)

		val testProjectName = "Test Project"
		component.createProject(testProjectName)

		assertEquals(testProjectName, projectNameSlot.captured)

		// TODO now verify that the project list is reloaded
	}
}