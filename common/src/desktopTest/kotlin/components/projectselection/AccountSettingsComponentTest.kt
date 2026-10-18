package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
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
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import listedProjectId
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.ForwardingFileSystem
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
import kotlin.time.Instant

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
	private lateinit var accountSynchronizer: ClientAccountSynchronizer

	/** Set by a test to interleave an action with the post-success wipe. */
	private var onFirstDelete: (() -> Unit)? = null

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

		onFirstDelete = null
		val hookedFs = DeleteHookFileSystem(ffs) { onFirstDelete?.invoke() }

		globalSettingsStore = mockk(relaxed = true)
		globalSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.globalSettingsUpdates } returns globalSettingsUpdates
		every { globalSettingsStore.globalSettings } answers { globalSettings }

		serverSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.serverSettingsUpdates } returns serverSettingsUpdates

		val spellCheckerFactory = mockk<PlatformSpellCheckerFactory>(relaxed = true)
		every { spellCheckerFactory.availableLocales() } returns emptyList()

		accountUseCase = mockk(relaxed = true)
		accountSynchronizer = mockk(relaxed = true)

		val testModule = module {
			single<FileSystem> { hookedFs }
			single { toml }
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single<Clock> { Clock.System }
			single {
				ProjectsRepository(
					fileSystem = hookedFs,
					globalSettingsStore = globalSettingsStore,
					projectsMetadataDatasource = ProjectMetadataDatasource(hookedFs, toml),
					toml = toml,
					json = createJsonSerializer(),
					deviceLocaleResolver = mockk(relaxed = true),
				)
			} bind ProjectsRepository::class
			single<ExampleProjectRepository> {
				FakeExampleProjectRepository(globalSettingsStore, hookedFs, toml, get())
			} bind ExampleProjectRepository::class
			single { accountUseCase } bind AccountUseCase::class
			single { accountSynchronizer } bind ClientAccountSynchronizer::class
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
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) } returns
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

	@Test
	fun `An http url logs in to a plaintext server`() = runTest {
		setupSucceeds()
		val component = newComponent()
		advanceUntilIdle()

		component.logIn(url = "http://192.168.1.50:8080")
		advanceUntilIdle()

		coVerify { accountUseCase.setupServer("192.168.1.50:8080", any(), any(), any(), any(), false) }
	}

	@Test
	fun `A bare host logs in over https`() = runTest {
		setupSucceeds()
		val component = newComponent()
		advanceUntilIdle()

		component.logIn(url = "hammer.ink")
		advanceUntilIdle()

		coVerify { accountUseCase.setupServer("hammer.ink", any(), any(), any(), any(), true) }
	}

	@Test
	fun `Typing an http url warns that it is insecure`() = runTest {
		val component = newComponent()
		advanceUntilIdle()

		component.updateServerUrl("http://192.168.1.50:8080")
		assertTrue(component.state.value.serverUrlInsecure)

		component.updateServerUrl("hammer.ink")
		assertFalse(component.state.value.serverUrlInsecure)
	}

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

	private val syncedAt = Instant.fromEpochSeconds(1_000_000)

	private fun writeJournal(projectName: String) {
		val journal = ProjectSynchronizationData(
			lastId = 3,
			newIds = emptyList(),
			lastSync = syncedAt,
			dirty = emptyList(),
			deletedIds = emptySet(),
			syncedHashes = mapOf(1 to "hash-on-the-old-server"),
		)
		ffs.write(projectsDir / projectName / SyncJournalDatasource.SYNC_FILE_NAME) {
			writeUtf8(createJsonSerializer().encodeToString(journal))
		}
	}

	private fun journalLastSync(projectName: String): Instant =
		ffs.read(projectsDir / projectName / SyncJournalDatasource.SYNC_FILE_NAME) {
			createJsonSerializer().decodeFromString<ProjectSynchronizationData>(readUtf8()).lastSync
		}

	@Test
	fun `Creating a new account resets sync state but keeps project ids`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		advanceUntilIdle()
		val projectA = assertNotNull(projectsRepository.findProject(PROJECT_A))
		projectsRepository.setProjectId(projectA, ProjectId("id-a"))
		writeJournal(PROJECT_A)

		component.createAccount()
		advanceUntilIdle()

		assertEquals(ProjectId("id-a"), projectsRepository.listedProjectId(projectA))
		assertEquals(Instant.DISTANT_PAST, journalLastSync(PROJECT_A))
		verify(exactly = 1) { accountSynchronizer.deleteSyncData() }
	}

	@Test
	fun `Logging in to a different server resets sync state but keeps project ids`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		advanceUntilIdle()
		val projectA = assertNotNull(projectsRepository.findProject(PROJECT_A))
		projectsRepository.setProjectId(projectA, ProjectId("keep-me"))
		writeJournal(PROJECT_A)

		component.logIn()
		advanceUntilIdle()
		component.chooseMerge()
		advanceUntilIdle()

		assertEquals(ProjectId("keep-me"), projectsRepository.listedProjectId(projectA))
		assertEquals(Instant.DISTANT_PAST, journalLastSync(PROJECT_A))
		verify(exactly = 1) { accountSynchronizer.deleteSyncData() }
	}

	@Test
	fun `Re-authenticating against the configured server keeps sync state`() = runTest {
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
		writeJournal(PROJECT_A)
		component.logIn()
		advanceUntilIdle()

		assertEquals(syncedAt, journalLastSync(PROJECT_A))
		verify(exactly = 0) { accountSynchronizer.deleteSyncData() }
	}

	@Test
	fun `Removing the server resets sync state`() = runTest {
		seedProject(PROJECT_A)

		val component = newComponent()
		advanceUntilIdle()
		writeJournal(PROJECT_A)
		component.removeServer()
		advanceUntilIdle()

		assertEquals(Instant.DISTANT_PAST, journalLastSync(PROJECT_A))
		verify(exactly = 1) { accountSynchronizer.deleteSyncData() }
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
	fun `Re-authenticating against the configured server keeps the example project`() = runTest {
		seedProject(ExampleProjectRepository.PROJECT_NAME)
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

		assertEquals(listOf(ExampleProjectRepository.PROJECT_NAME), localProjectNames())
	}

	@Test
	fun `Cancelling while the post-success wipe runs keeps the new credentials`() = runTest {
		seedProject(PROJECT_A)
		setupSucceeds()

		val component = newComponent()
		advanceUntilIdle()
		// The wipe runs off the main dispatcher, so a close or ESC can land while it is in flight.
		// By then the account exists and its tokens must survive.
		onFirstDelete = { component.cancelServerSetup() }
		component.logIn(replaceLocalContent = true)
		advanceUntilIdle()

		verify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
		assertEquals(emptyList(), localProjectNames())
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


	private val challenge = MfaChallenge(
		token = "challenge-token",
		methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY),
	)

	private fun setupNeedsSecondFactor() {
		coEvery { accountUseCase.setupServer(any(), any(), any(), any(), any(), any()) } returns
			ServerSetupResult.MfaRequired(challenge)
	}

	/** Opens the dialog and logs in, the way the UI reaches the code step. */
	private fun AccountSettingsComponent.openAndLogIn() {
		beginSetupServer()
		logIn()
	}

	@Test
	fun `A second-factor challenge keeps the dialog open on the code step`() = runTest {
		setupNeedsSecondFactor()

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()

		assertEquals(challenge, component.state.value.mfaChallenge)
		assertTrue(component.state.value.serverSetup)
		assertFalse(component.state.value.serverWorking)
		assertNull(component.state.value.serverError)
		verify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `Submitting the code finishes the login`() = runTest {
		setupNeedsSecondFactor()
		coEvery { accountUseCase.completeMfa("123456") } returns ServerSetupResult.Success

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.submitMfaCode(" 123456 ")
		advanceUntilIdle()

		assertNull(component.state.value.mfaChallenge)
		assertFalse(component.state.value.serverSetup)
		assertFalse(component.state.value.serverWorking)
		coVerify { accountUseCase.completeMfa("123456") }
		verify(exactly = 0) { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `A refused code shows the message and stays on the code step`() = runTest {
		setupNeedsSecondFactor()
		coEvery { accountUseCase.completeMfa(any()) } returns
			ServerSetupResult.MfaRequired(challenge, error = "That code did not work".toMsg())

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.submitMfaCode("000000")
		advanceUntilIdle()

		assertEquals("That code did not work", component.state.value.serverError)
		assertEquals(challenge, component.state.value.mfaChallenge)
		assertTrue(component.state.value.serverSetup)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `A failure while completing the second factor returns to the password step`() = runTest {
		setupNeedsSecondFactor()
		coEvery { accountUseCase.completeMfa(any()) } returns
			ServerSetupResult.Failure(displayMessage = "Account pending deletion".toMsg(), exception = null)

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.submitMfaCode("123456")
		advanceUntilIdle()

		assertNull(component.state.value.mfaChallenge)
		assertTrue(component.state.value.serverSetup)
		assertEquals("Account pending deletion", component.state.value.serverError)
	}

	@Test
	fun `Submitting a blank code does nothing`() = runTest {
		setupNeedsSecondFactor()

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.submitMfaCode("   ")
		advanceUntilIdle()

		coVerify(exactly = 0) { accountUseCase.completeMfa(any()) }
		assertEquals(challenge, component.state.value.mfaChallenge)
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `Submitting a code with no challenge pending does nothing`() = runTest {
		val component = newComponent()
		component.submitMfaCode("123456")
		advanceUntilIdle()

		coVerify(exactly = 0) { accountUseCase.completeMfa(any()) }
		assertFalse(component.state.value.serverWorking)
	}

	@Test
	fun `Backing out of the second factor returns to the password step and drops the provisional settings`() = runTest {
		setupNeedsSecondFactor()

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.cancelMfa()
		advanceUntilIdle()

		assertNull(component.state.value.mfaChallenge)
		assertTrue(component.state.value.serverSetup)
		verify { accountUseCase.cancelMfa() }
		verify { globalSettingsStore.deleteServerSettings() }
	}

	@Test
	fun `Closing the dialog during the second factor drops the challenge`() = runTest {
		setupNeedsSecondFactor()

		val component = newComponent()
		component.openAndLogIn()
		advanceUntilIdle()
		component.cancelServerSetup()
		advanceUntilIdle()

		assertNull(component.state.value.mfaChallenge)
		assertFalse(component.state.value.serverSetup)
		verify { accountUseCase.cancelMfa() }
		verify { globalSettingsStore.deleteServerSettings() }
	}

	private companion object {
		const val PROJECT_A = "Test Project A"
		const val PROJECT_B = "Test Project B"
	}
}

/** Runs [onFirstDelete] once, on the first deletion, so a test can act from inside a wipe. */
private class DeleteHookFileSystem(
	delegate: FileSystem,
	private val onFirstDelete: () -> Unit,
) : ForwardingFileSystem(delegate) {
	private var fired = false

	override fun delete(path: Path, mustExist: Boolean) {
		if (!fired) {
			fired = true
			onFirstDelete()
		}
		super.delete(path, mustExist)
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
