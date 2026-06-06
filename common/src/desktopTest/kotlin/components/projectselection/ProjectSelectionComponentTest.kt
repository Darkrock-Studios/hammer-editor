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
import com.darkrockstudios.apps.hammer.common.data.versioncheck.GithubReleaseInfo
import com.darkrockstudios.apps.hammer.common.data.versioncheck.ShouldNotifyOfUpdateUseCase
import com.darkrockstudios.apps.hammer.common.data.versioncheck.VersionCheckRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import getProjectsDirectory
import io.mockk.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
	lateinit var versionCheckRepository: VersionCheckRepository
	lateinit var versionCheckUpdates: MutableSharedFlow<VersionCheckRepository.VersionCheckResult>
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
		versionCheckRepository = mockk()
		urlLauncher = mockk(relaxed = true)

		versionCheckUpdates = MutableSharedFlow(
			extraBufferCapacity = 1,
			replay = 1,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)
		every { versionCheckRepository.updates } returns versionCheckUpdates

		val testModule = module {
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single { projectsRepository } bind ProjectsRepository::class
			single { exampleProjectRepository } bind ExampleProjectRepository::class
			single { projectsSynchronizer }
			single { versionCheckRepository }
			single { urlLauncher } bind UrlLauncher::class
			factory { ShouldNotifyOfUpdateUseCase() }
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

	private fun release(tag: String) = GithubReleaseInfo(
		tagName = tag,
		name = "Release $tag",
		body = "Notes for $tag",
		htmlUrl = "https://github.com/Wavesonics/hammer-editor/releases/tag/$tag",
	)

	@Test
	fun `Dialog visible on launch when new version available and not previously dismissed`() = runTest {
		coEvery { versionCheckRepository.checkForUpdate(any()) } answers {
			val result = VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v99.0.0"),
				isNewVersionAvailable = true,
				currentVersion = "v1.0.0",
			)
			versionCheckUpdates.tryEmit(result)
			result
		}

		val component = newComponent()
		advanceUntilIdle()

		val state = component.updateNotification.value
		assertTrue(state.visible)
		assertEquals("v99.0.0", state.latestVersionTag)
		assertEquals("Notes for v99.0.0", state.releaseBody)
	}

	@Test
	fun `Dialog hidden when latest tag matches lastDismissedUpdateVersion`() = runTest {
		globalSettings = globalSettings.copy(lastDismissedUpdateVersion = "v99.0.0")
		coEvery { versionCheckRepository.checkForUpdate(any()) } returns
			VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v99.0.0"),
				isNewVersionAvailable = true,
				currentVersion = "v1.0.0",
			)

		val component = newComponent()
		advanceUntilIdle()

		assertFalse(component.updateNotification.value.visible)
	}

	@Test
	fun `Dialog hidden when no new version is available`() = runTest {
		coEvery { versionCheckRepository.checkForUpdate(any()) } returns
			VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v1.0.0"),
				isNewVersionAvailable = false,
				currentVersion = "v1.0.0",
			)

		val component = newComponent()
		advanceUntilIdle()

		assertFalse(component.updateNotification.value.visible)
	}

	@Test
	fun `dismissUpdateNotification with remember writes lastDismissedUpdateVersion`() = runTest {
		coEvery { versionCheckRepository.checkForUpdate(any()) } returns
			VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v99.0.0"),
				isNewVersionAvailable = true,
				currentVersion = "v1.0.0",
			)
		val captured = slot<(GlobalSettings) -> GlobalSettings>()
		coEvery { globalSettingsStore.updateSettings(capture(captured)) } just Runs

		val component = newComponent()
		advanceUntilIdle()
		component.dismissUpdateNotification(remember = true)
		advanceUntilIdle()

		assertFalse(component.updateNotification.value.visible)
		val updated = captured.captured(globalSettings)
		assertEquals("v99.0.0", updated.lastDismissedUpdateVersion)
	}

	@Test
	fun `dismissUpdateNotification without remember does not write settings`() = runTest {
		coEvery { versionCheckRepository.checkForUpdate(any()) } returns
			VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v99.0.0"),
				isNewVersionAvailable = true,
				currentVersion = "v1.0.0",
			)

		val component = newComponent()
		advanceUntilIdle()
		component.dismissUpdateNotification(remember = false)
		advanceUntilIdle()

		assertFalse(component.updateNotification.value.visible)
		coVerify(exactly = 0) { globalSettingsStore.updateSettings(any()) }
	}

	@Test
	fun `openReleaseUrl launches the release URL`() = runTest {
		coEvery { versionCheckRepository.checkForUpdate(any()) } returns
			VersionCheckRepository.VersionCheckResult(
				latestRelease = release("v99.0.0"),
				isNewVersionAvailable = true,
				currentVersion = "v1.0.0",
			)

		val component = newComponent()
		advanceUntilIdle()
		component.openReleaseUrl()

		coVerify(exactly = 1) {
			urlLauncher.openInBrowser("https://github.com/Wavesonics/hammer-editor/releases/tag/v99.0.0")
		}
	}
}
