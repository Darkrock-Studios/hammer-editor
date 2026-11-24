package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.nux.NuxFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import com.darkrockstudios.apps.hammer.common.data.nux.NuxState
import com.darkrockstudios.apps.hammer.common.data.nux.ResolveIntroUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import com.darkrockstudios.apps.hammer.common.data.changelog.Changelog
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogDatasource
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
import utils.FakeAppUpdater

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
	lateinit var appUpdater: FakeAppUpdater

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
		appUpdater = FakeAppUpdater()

		val testModule = module {
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single { projectsRepository } bind ProjectsRepository::class
			single { exampleProjectRepository } bind ExampleProjectRepository::class
			single { projectsSynchronizer }
			factory { changelogRepository }
			single { urlLauncher } bind UrlLauncher::class
			single<AppUpdater> { appUpdater }
			single { NuxRepository(NuxFilesystemDatasource(ffs, toml), globalSettingsStore) }
			single { ResolveIntroUseCase(get(), get()) }
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
		// An existing user by default: the intro and its changelog handling stay out of the way.
		every { projectsRepository.isOnlyProject(ExampleProjectRepository.PROJECT_NAME) } returns false
		every { projectsRepository.getProjectTree(any()) } returns ProjectTree.EMPTY
		every { exampleProjectRepository.shouldInstallFirstTime() } returns false
	}

	private fun newComponent(componentContext: DefaultComponentContext = context): ProjectSelectionComponent =
		ProjectSelectionComponent(
			componentContext = componentContext,
			onProjectSelected = {},
		)

	@Test
	fun `app update state mirrors the updater`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns false

		val component = newComponent()
		advanceUntilIdle()
		assertEquals(AppUpdateState.Idle, component.appUpdate.value)

		val available = AppUpdateState.Available(version = "9.9.9", installable = true)
		appUpdater.state.value = available
		advanceUntilIdle()

		assertEquals(available, component.appUpdate.value)
	}

	@Test
	fun `update and dismiss are handed to the updater`() = runTest {
		coEvery { changelogRepository.hasUnseenChangelog() } returns false

		val component = newComponent()
		advanceUntilIdle()
		component.updateApp()
		component.dismissUpdate()

		assertEquals(1, appUpdater.updates)
		assertEquals(1, appUpdater.dismissals)
	}

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

	private fun storedNuxState(): NuxState = NuxFilesystemDatasource(ffs, toml).load()

	/** A new user: nothing on disk but the example project, which [firstInstall] installs this launch. */
	private fun newUser(firstInstall: Boolean = true) {
		every { exampleProjectRepository.shouldInstallFirstTime() } returns firstInstall
		every { projectsRepository.findProject(ExampleProjectRepository.PROJECT_NAME) } returns null
		every { exampleProjectRepository.install(any()) } just Runs
		every { projectsRepository.isOnlyProject(ExampleProjectRepository.PROJECT_NAME) } returns true
	}

	private fun hasOwnProjects() {
		every { projectsRepository.isOnlyProject(ExampleProjectRepository.PROJECT_NAME) } returns false
	}

	private val ProjectSelectionComponent.stage get() = introStage.value

	private fun ProjectSelectionComponent.showingIntro() = stage is ProjectSelection.IntroStage.Showing

	private fun ProjectSelectionComponent.shownIntro() = (stage as ProjectSelection.IntroStage.Showing).intro

	@Test
	fun `The intro shows on a fresh install`() = runTest(mainTestDispatcher) {
		newUser()

		val component = newComponent()
		val stageBeforeDeciding = component.stage
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Deciding, stageBeforeDeciding)
		assertTrue(component.showingIntro())
		assertFalse(storedNuxState().introShown)
	}

	@Test
	fun `A user who left the intro unfinished sees it on the next launch, over the project list`() = runTest(mainTestDispatcher) {
		newUser(firstInstall = false)

		val component = newComponent()
		val stageAtLaunch = component.stage
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, stageAtLaunch)
		assertTrue(component.showingIntro())
		assertFalse(storedNuxState().introShown)
	}

	@Test
	fun `An existing install shows the project list at once`() = runTest(mainTestDispatcher) {
		val component = newComponent()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
	}

	@Test
	fun `A user with projects never sees the intro, and it is marked shown`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
		assertTrue(storedNuxState().introShown)
	}

	@Test
	fun `A returning user with projects is passed over even on a first install`() = runTest(mainTestDispatcher) {
		newUser()
		hasOwnProjects()

		val component = newComponent()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
		assertTrue(storedNuxState().introShown)
	}

	@Test
	fun `The intro does not show again once it has been shown`() = runTest(mainTestDispatcher) {
		NuxFilesystemDatasource(ffs, toml).store(NuxState(introShown = true))
		newUser()

		val component = newComponent()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
	}

	@Test
	fun `A failed intro decision falls back to the project list`() = runTest(mainTestDispatcher) {
		newUser()
		every { projectsRepository.isOnlyProject(any()) } throws IllegalStateException("unreadable")

		val component = newComponent()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
	}

	@Test
	fun `Finishing the intro marks it shown and goes on to the project list`() = runTest(mainTestDispatcher) {
		newUser()
		val component = newComponent()
		advanceUntilIdle()
		val intro = component.shownIntro()

		repeat(Intro.Slide.entries.size) { intro.next() }
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
		assertTrue(component.stack.value.active.instance is ProjectSelection.Destination.ProjectsListDestination)
		assertTrue(storedNuxState().introShown)
	}

	@Test
	fun `Leaving the intro early marks it shown and goes on to the project list`() = runTest(mainTestDispatcher) {
		newUser()
		val component = newComponent()
		advanceUntilIdle()

		component.shownIntro().leave()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
		assertTrue(component.stack.value.active.instance is ProjectSelection.Destination.ProjectsListDestination)
		assertTrue(storedNuxState().introShown)
	}

	/** A real changelog for [version], read against the test's stored settings. */
	private fun bakedChangelog(version: String) {
		val datasource = object : ChangelogDatasource {
			override suspend fun loadChangelog() = Changelog(version = version, date = "2026-11-22", notes = "- A thing")
		}
		changelogRepository = ChangelogRepository(datasource, globalSettingsStore)
		coEvery { globalSettingsStore.updateSettings(any()) } answers {
			globalSettings = firstArg<(GlobalSettings) -> GlobalSettings>()(globalSettings)
		}
	}

	@Test
	fun `A fresh install does not show the changelog`() = runTest(mainTestDispatcher) {
		newUser()
		bakedChangelog("4.0.0")

		val component = newComponent()
		advanceUntilIdle()

		assertFalse(component.changelog.value.visible)
		assertEquals("4.0.0", globalSettings.lastSeenChangelogVersion)
	}

	@Test
	fun `An upgrade from an older version still shows the changelog`() = runTest(mainTestDispatcher) {
		globalSettings = globalSettings.copy(lastSeenChangelogVersion = "3.9.0")
		bakedChangelog("4.0.0")

		val component = newComponent()
		advanceUntilIdle()

		assertTrue(component.changelog.value.visible)
		assertEquals("4.0.0", component.changelog.value.version)
	}

	@Test
	fun `A returning user with projects still sees What's new after their settings were reset`() = runTest(mainTestDispatcher) {
		newUser()
		hasOwnProjects()
		bakedChangelog("4.0.0")

		val component = newComponent()
		advanceUntilIdle()

		assertTrue(component.changelog.value.visible)
	}

	@Test
	fun `After a fresh install, the next version's changelog shows`() = runTest(mainTestDispatcher) {
		newUser()
		bakedChangelog("4.0.0")
		newComponent().also { advanceUntilIdle() }.shownIntro().leave()
		advanceUntilIdle()

		every { exampleProjectRepository.shouldInstallFirstTime() } returns false
		bakedChangelog("4.1.0")
		val updated = newComponent(DefaultComponentContext(LifecycleRegistry().apply { resume() }))
		advanceUntilIdle()

		assertTrue(updated.changelog.value.visible)
		assertEquals("4.1.0", updated.changelog.value.version)
	}

	/** An existing install whose user deleted every project, the example included. */
	private fun noProjectsLeft() {
		every { projectsRepository.isOnlyProject(ExampleProjectRepository.PROJECT_NAME) } returns false
	}

	@Test
	fun `An existing install with no projects left never sees the intro, and it is marked shown`() = runTest(mainTestDispatcher) {
		noProjectsLeft()

		val component = newComponent()
		advanceUntilIdle()

		assertEquals(ProjectSelection.IntroStage.Done, component.stage)
		assertTrue(storedNuxState().introShown)
	}

	@Test
	fun `An upgrader with only the example sees the intro and still gets What's new`() = runTest(mainTestDispatcher) {
		newUser(firstInstall = false)
		globalSettings = globalSettings.copy(lastSeenChangelogVersion = "3.9.0")
		bakedChangelog("4.0.0")

		val component = newComponent()
		advanceUntilIdle()

		assertTrue(component.showingIntro())
		assertTrue(component.changelog.value.visible)
	}

	@Test
	fun `Even an error that is not an exception lets the project list show`() {
		newUser()
		every { projectsRepository.isOnlyProject(any()) } throws NotImplementedError("broken")
		var stage: ProjectSelection.IntroStage? = null

		runCatching {
			runTest(mainTestDispatcher) {
				val component = newComponent()
				advanceUntilIdle()
				stage = component.stage
			}
		}

		assertEquals(ProjectSelection.IntroStage.Done, stage)
	}
}
