package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.changelog.Changelog
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import getProjectsDirectory
import io.mockk.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectSelectionComponentTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var lifecycle: LifecycleRegistry
	lateinit var context: DefaultComponentContext

	lateinit var globalSettingsStore: GlobalSettingsStore
	lateinit var globalSettingsUpdates: SharedFlow<GlobalSettings>
	lateinit var serverSettingsUpdates: SharedFlow<ServerSettings?>
	lateinit var projectsRepository: ProjectsRepository
	lateinit var exampleProjectRepository: ExampleProjectRepository
	lateinit var projectsSynchronizer: ClientAccountSynchronizer
	lateinit var changelogRepository: ChangelogRepository
	lateinit var urlLauncher: UrlLauncher

	private var globalSettings: GlobalSettings = GlobalSettings(
		projectsDirectory = "",
		spellCheckSettings = SpellCheckerSettings(locale = mockk())
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()

		lifecycle = LifecycleRegistry()
		context = DefaultComponentContext(lifecycle = lifecycle)

		globalSettingsStore = mockk(relaxed = true)
		projectsRepository = mockk()
		exampleProjectRepository = mockk()
		projectsSynchronizer = mockk()
		changelogRepository = mockk(relaxed = true)
		urlLauncher = mockk(relaxed = true)

		val testModule = module {
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single { projectsRepository } bind ProjectsRepository::class
			single { exampleProjectRepository } bind ExampleProjectRepository::class
			single { projectsSynchronizer }
			single { changelogRepository }
			single { urlLauncher } bind UrlLauncher::class
		}
		setupKoin(testModule)
		lifecycle.resume()

		val projectsDir = getProjectsDirectory()
		every { projectsRepository.getProjectsDirectory() } returns projectsDir.toHPath()
		ffs.createDirectories(projectsDir)

		every { projectsSynchronizer.isServerSynchronized() } returns false

		globalSettingsUpdates = mockk()
		coEvery { globalSettingsUpdates.collect(any()) } just Awaits
		every { globalSettingsStore.globalSettingsUpdates } returns globalSettingsUpdates

		globalSettings = GlobalSettings(
			projectsDirectory = projectsDir.toString(),
			spellCheckSettings = SpellCheckerSettings(locale = mockk())
		)
		every { globalSettingsStore.globalSettings } answers { globalSettings }

		serverSettingsUpdates = mockk()
		coEvery { serverSettingsUpdates.collect(any()) } just Awaits
		coEvery { serverSettingsUpdates.first() } returns null
		every { globalSettingsStore.serverSettingsUpdates } returns serverSettingsUpdates

		every { projectsRepository.getProjects(any()) } returns emptyList()
		every { exampleProjectRepository.shouldInstallFirstTime() } returns false
	}

	private fun newComponent(): ProjectSelectionComponent =
		ProjectSelectionComponent(
			componentContext = context,
			onProjectSelected = {},
		)

	private val changelog = Changelog(
		version = "99.0.0",
		date = "2026-8-4",
		notes = "[New]\n- A thing",
	)

	@Test
	fun `Dialog visible on launch when the baked changelog has not been seen`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns true
		coEvery { changelogRepository.getChangelog() } returns changelog

		val component = newComponent()
		advanceUntilIdle()

		val state = component.changelog.value
		assertTrue(state.visible)
		assertEquals("99.0.0", state.version)
		assertEquals("2026-8-4", state.date)
		assertEquals("[New]\n- A thing", state.notes)
	}

	@Test
	fun `Dialog hidden on launch when the changelog has already been seen`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns false

		val component = newComponent()
		advanceUntilIdle()

		assertFalse(component.changelog.value.visible)
		coVerify(exactly = 0) { changelogRepository.getChangelog() }
	}

	@Test
	fun `Launch never checks GitHub for a version`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns true
		coEvery { changelogRepository.getChangelog() } returns changelog

		newComponent()
		advanceUntilIdle()

		// The whole point of baking the changelog in: startup makes no network request.
		coVerify(exactly = 0) { urlLauncher.openInBrowser(any()) }
	}

	@Test
	fun `dismissChangelog hides the dialog and marks it seen`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns true
		coEvery { changelogRepository.getChangelog() } returns changelog

		val component = newComponent()
		advanceUntilIdle()
		component.dismissChangelog()
		advanceUntilIdle()

		assertFalse(component.changelog.value.visible)
		coVerify(exactly = 1) { changelogRepository.markSeen() }
	}

	@Test
	fun `showChangelog reopens the dialog after it was dismissed`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns false
		coEvery { changelogRepository.getChangelog() } returns changelog

		val component = newComponent()
		advanceUntilIdle()
		assertFalse(component.changelog.value.visible)

		component.showChangelog()
		advanceUntilIdle()

		assertTrue(component.changelog.value.visible)
		assertEquals("99.0.0", component.changelog.value.version)
	}

	@Test
	fun `openLatestRelease launches the releases page`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns false

		val component = newComponent()
		advanceUntilIdle()
		component.openLatestRelease()

		coVerify(exactly = 1) {
			urlLauncher.openInBrowser("https://github.com/Darkrock-Studios/hammer-editor/releases/latest")
		}
	}
}
