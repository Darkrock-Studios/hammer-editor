package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.NewUserExperience
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.get
import utils.BaseTest
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsComponentTest : BaseTest() {

	private lateinit var lifecycle: LifecycleRegistry
	private lateinit var context: DefaultComponentContext

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var globalSettingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var serverSettingsUpdates: MutableSharedFlow<ServerSettings?>
	private lateinit var accountUseCase: AccountUseCase
	private lateinit var projectsRepository: ProjectsRepository

	private val projectsDir: Path =
		getDefaultRootDocumentDirectory().toPath() / GlobalSettingsStore.DEFAULT_PROJECTS_DIR

	private var globalSettings = GlobalSettings(
		projectsDirectory = projectsDir.toString(),
		spellCheckSettings = SpellCheckerSettings(locale = mockk()),
		automaticSyncing = true,
		autoCloseSyncDialog = true,
		automaticBackups = true,
		nux = NewUserExperience(exampleProjectCreated = true),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		lifecycle = LifecycleRegistry()
		context = DefaultComponentContext(lifecycle = lifecycle)

		ffs = FakeFileSystem()
		ffs.createDirectories(projectsDir)
		toml = createTomlSerializer()

		globalSettingsStore = mockk(relaxed = true)
		globalSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.globalSettingsUpdates } returns globalSettingsUpdates
		every { globalSettingsStore.globalSettings } answers { globalSettings }

		serverSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.serverSettingsUpdates } returns serverSettingsUpdates

		val spellCheckerFactory = mockk<PlatformSpellCheckerFactory>(relaxed = true)
		every { spellCheckerFactory.availableLocales() } returns emptyList()

		accountUseCase = mockk(relaxed = true)

		val testModule = module {
			single<FileSystem> { ffs }
			single { toml }
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single<Clock> { Clock.System }
			single {
				ProjectsRepository(
					fileSystem = ffs,
					globalSettingsStore = globalSettingsStore,
					projectsMetadataDatasource = ProjectMetadataDatasource(ffs, toml),
					toml = toml,
					deviceLocaleResolver = mockk(relaxed = true),
				)
			} bind ProjectsRepository::class
			single<ExampleProjectRepository> {
				FakeExampleProjectRepository(globalSettingsStore, ffs, toml, get())
			} bind ExampleProjectRepository::class
			single { accountUseCase } bind AccountUseCase::class
			single { mockk<StrRes>(relaxed = true) } bind StrRes::class
			single { spellCheckerFactory }
			factory { mockk<PlatformSettings>(relaxed = true) } bind PlatformSettings::class
		}
		setupKoin(testModule)
		projectsRepository = get()
		lifecycle.resume()
	}

	private fun newComponent() = AccountSettingsComponent(componentContext = context)

	private fun seedProject(name: String) {
		val dir = projectsDir / name
		ffs.createDirectories(dir)
		ffs.write(dir / ProjectMetadata.FILENAME) { writeUtf8("") }
	}

	private fun localProjectNames(): List<String> =
		ffs.list(projectsDir).map { it.name }.sorted()

	private fun setupSucceeds() {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Success
	}

	private fun AccountSettingsComponent.logIn(
		url: String = "example.com",
		email: String = "writer@example.com",
		replaceLocalContent: Boolean = false,
	) = setupServer(
		url = url,
		email = email,
		password = "Password1!",
		create = false,
		replaceLocalContent = replaceLocalContent,
	)

	private fun AccountSettingsComponent.createAccount() = setupServer(
		url = "example.com",
		email = "writer@example.com",
		password = "Password1!",
		create = true,
		replaceLocalContent = false,
	)

	@Test
	fun `Auto-sync setting update is reflected in state`() = runTest {
		val component = newComponent()
		advanceUntilIdle()
		assertTrue(component.state.value.syncAutomaticSync)

		globalSettingsUpdates.emit(globalSettings.copy(automaticSyncing = false))
		advanceUntilIdle()

		assertFalse(component.state.value.syncAutomaticSync)
	}

	@Test
	fun `Auto-close sync dialog setting update is reflected in state`() = runTest {
		val component = newComponent()
		advanceUntilIdle()
		assertTrue(component.state.value.syncAutoCloseDialog)

		globalSettingsUpdates.emit(globalSettings.copy(autoCloseSyncDialog = false))
		advanceUntilIdle()

		assertFalse(component.state.value.syncAutoCloseDialog)
	}

	@Test
	fun `Creating a new account clears stale server project IDs`() = runTest {
		seedProject(PROJECT_A)
		seedProject(PROJECT_B)
		setupSucceeds()

		val component = newComponent()
		advanceUntilIdle()
		projectsRepository.setProjectId(
			projectsRepository.getProjectDefinition(PROJECT_A),
			ProjectId("stale-a"),
		)
		projectsRepository.setProjectId(
			projectsRepository.getProjectDefinition(PROJECT_B),
			ProjectId("stale-b"),
		)

		component.createAccount()
		advanceUntilIdle()

		assertNull(projectsRepository.getProjectId(projectsRepository.getProjectDefinition(PROJECT_A)))
		assertNull(projectsRepository.getProjectId(projectsRepository.getProjectDefinition(PROJECT_B)))
	}

	@Test
	fun `Logging in to an existing account leaves server project IDs untouched`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		advanceUntilIdle()
		val projectA = projectsRepository.getProjectDefinition(PROJECT_A)
		projectsRepository.setProjectId(projectA, ProjectId("keep-me"))

		component.logIn()
		advanceUntilIdle()
		component.chooseMerge()
		advanceUntilIdle()

		assertEquals(ProjectId("keep-me"), projectsRepository.getProjectId(projectA))
	}

	@Test
	fun `A failed login leaves local projects untouched`() = runTest {
		seedProject(PROJECT_A)
		seedProject(PROJECT_B)
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Failure(displayMessage = null, exception = null)

		val component = newComponent()
		component.logIn(replaceLocalContent = true)
		advanceUntilIdle()

		assertEquals(listOf(PROJECT_A, PROJECT_B), localProjectNames())
	}

	@Test
	fun `Logging in with only the example project silently drops it`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
		setupSucceeds()

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		assertEquals(emptyList(), localProjectNames())
	}

	@Test
	fun `A failed login with only the example project keeps it`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any()) } returns
			ServerSetupResult.Failure(displayMessage = null, exception = null)

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()

		assertEquals(listOf(ExampleProjectRepository.PROJECT_NAME), localProjectNames())
	}

	@Test
	fun `Declining terms of service keeps the example project`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), null)
		} returns ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Legal text", version = "v1"))

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.declineTos()
		advanceUntilIdle()

		assertEquals(listOf(ExampleProjectRepository.PROJECT_NAME), localProjectNames())
	}

	@Test
	fun `Accepting terms of service still drops the example project`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), null)
		} returns ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Legal text", version = "v1"))
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), "v1")
		} returns ServerSetupResult.Success

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.acceptTos()
		advanceUntilIdle()

		assertEquals(emptyList(), localProjectNames())
	}

	@Test
	fun `Creating an account with only the example project keeps it`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
		setupSucceeds()

		val component = newComponent()
		component.createAccount()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		assertEquals(listOf(ExampleProjectRepository.PROJECT_NAME), localProjectNames())
	}

	@Test
	fun `Creating an account with real projects never prompts`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		component.createAccount()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		assertEquals(listOf(PROJECT_A), localProjectNames())
	}

	@Test
	fun `Logging in to a new server with real projects raises the merge prompt`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()

		assertTrue(component.state.value.mergePrompt)
		assertFalse(component.state.value.serverSetup)
		coVerify(exactly = 0) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Choosing merge runs the setup and keeps every local project`() = runTest {
		seedProject(PROJECT_A)
		seedProject(PROJECT_B)
		setupSucceeds()

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.chooseMerge()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		coVerify(exactly = 1) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
		assertEquals(listOf(PROJECT_A, PROJECT_B), localProjectNames())
	}

	@Test
	fun `Choosing replace deletes every local project`() = runTest {
		seedProject(PROJECT_A)
		seedProject(PROJECT_B)
		setupSucceeds()

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.chooseReplace()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		assertEquals(emptyList(), localProjectNames())
	}

	@Test
	fun `Cancelling the merge prompt returns to setup without contacting the server`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		component.logIn()
		advanceUntilIdle()
		component.cancelMergePrompt()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		assertTrue(component.state.value.serverSetup)
		coVerify(exactly = 0) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
		assertEquals(listOf(PROJECT_A), localProjectNames())
	}

	@Test
	fun `Re-authenticating against the configured server never prompts`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()
		serverSettingsUpdates.emit(
			ServerSettings(
				url = "example.com",
				email = "writer@example.com",
				userId = 1L,
				bearerToken = null,
				refreshToken = null,
			)
		)

		val component = newComponent()
		advanceUntilIdle()
		component.logIn()
		advanceUntilIdle()

		assertFalse(component.state.value.mergePrompt)
		coVerify(exactly = 1) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
		assertEquals(listOf(PROJECT_A), localProjectNames())
	}

	@Test
	fun `Terms of service challenge is surfaced and acceptance retries the request`() = runTest {
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), null)
		} returns ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Legal text", version = "v1"))
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), "v1")
		} returns ServerSetupResult.Success

		val component = newComponent()
		component.createAccount()
		advanceUntilIdle()

		assertEquals("Legal text", component.state.value.tosChallenge?.text)
		assertEquals("v1", component.state.value.tosChallenge?.version)
		assertFalse(component.state.value.serverWorking)

		component.acceptTos()
		advanceUntilIdle()

		assertNull(component.state.value.tosChallenge)
		assertFalse(component.state.value.serverSetup)
		coVerify { accountUseCase.setupServer(any(), any(), any(), any(), "v1") }
	}

	@Test
	fun `Declining terms of service clears the challenge without creating an account`() = runTest {
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), null)
		} returns ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Legal text", version = "v1"))

		val component = newComponent()
		component.createAccount()
		advanceUntilIdle()
		assertNotNull(component.state.value.tosChallenge)

		component.declineTos()
		advanceUntilIdle()

		assertNull(component.state.value.tosChallenge)
		// Declining discards the provisional server settings setupServer persisted for the retry.
		verify { globalSettingsStore.deleteServerSettings() }
		// Only the initial challenge attempt ran; declining never resubmits.
		coVerify(exactly = 1) { accountUseCase.setupServer(any(), any(), any(), any(), any()) }
	}

	private companion object {
		const val PROJECT_A = "Test Project A"
		const val PROJECT_B = "Test Project B"
	}
}

private class FakeExampleProjectRepository(
	globalSettingsStore: GlobalSettingsStore,
	fileSystem: FileSystem,
	toml: Toml,
	clock: Clock,
) : ExampleProjectRepository(globalSettingsStore, fileSystem, toml, clock) {
	override fun removeExampleProject() {
		fileSystem.deleteRecursively(projectsDir() / PROJECT_NAME)
	}

	override fun platformInstall() {
		val dir = projectsDir() / PROJECT_NAME
		fileSystem.createDirectories(dir)
		fileSystem.write(dir / ProjectMetadata.FILENAME) { writeUtf8("") }
	}
}
