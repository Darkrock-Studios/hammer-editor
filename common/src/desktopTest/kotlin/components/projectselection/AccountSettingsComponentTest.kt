package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsComponentTest : BaseTest() {

	private lateinit var lifecycle: LifecycleRegistry
	private lateinit var context: DefaultComponentContext

	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var globalSettingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var serverSettingsUpdates: SharedFlow<ServerSettings?>
	private lateinit var accountUseCase: AccountUseCase
	private lateinit var projectsRepository: ProjectsRepository

	private var globalSettings = GlobalSettings(
		projectsDirectory = "",
		spellCheckSettings = SpellCheckerSettings(locale = mockk()),
		automaticSyncing = true,
		autoCloseSyncDialog = true,
		automaticBackups = true,
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		lifecycle = LifecycleRegistry()
		context = DefaultComponentContext(lifecycle = lifecycle)

		globalSettingsStore = mockk(relaxed = true)
		globalSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.globalSettingsUpdates } returns globalSettingsUpdates
		every { globalSettingsStore.globalSettings } answers { globalSettings }

		serverSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsStore.serverSettingsUpdates } returns serverSettingsUpdates

		val spellCheckerFactory = mockk<PlatformSpellCheckerFactory>(relaxed = true)
		every { spellCheckerFactory.availableLocales() } returns emptyList()

		accountUseCase = mockk(relaxed = true)
		projectsRepository = mockk(relaxed = true)

		val testModule = module {
			single { globalSettingsStore } bind GlobalSettingsStore::class
			single { mockk<ExampleProjectRepository>(relaxed = true) } bind ExampleProjectRepository::class
			single { accountUseCase } bind AccountUseCase::class
			single { projectsRepository } bind ProjectsRepository::class
			single { mockk<StrRes>(relaxed = true) } bind StrRes::class
			single { spellCheckerFactory }
			factory { mockk<PlatformSettings>(relaxed = true) } bind PlatformSettings::class
		}
		setupKoin(testModule)
		lifecycle.resume()
	}

	private fun newComponent() = AccountSettingsComponent(componentContext = context)

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
		val projectA = mockk<ProjectDef>()
		val projectB = mockk<ProjectDef>()
		every { projectsRepository.getProjects() } returns listOf(projectA, projectB)
		coEvery {
			accountUseCase.setupServer(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns ServerSetupResult.Success

		val component = newComponent()
		component.setupServer(
			url = "example.com",
			email = "writer@example.com",
			password = "Password1!",
			create = true,
			removeLocalContent = false,
		)
		advanceUntilIdle()

		verify { projectsRepository.removeProjectId(projectA) }
		verify { projectsRepository.removeProjectId(projectB) }
	}

	@Test
	fun `Logging in to an existing account leaves server project IDs untouched`() = runTest {
		val projectA = mockk<ProjectDef>()
		every { projectsRepository.getProjects() } returns listOf(projectA)
		coEvery {
			accountUseCase.setupServer(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns ServerSetupResult.Success

		val component = newComponent()
		component.setupServer(
			url = "example.com",
			email = "writer@example.com",
			password = "Password1!",
			create = false,
			removeLocalContent = false,
		)
		advanceUntilIdle()

		verify(exactly = 0) { projectsRepository.removeProjectId(any()) }
	}

	@Test
	fun `Terms of service challenge is surfaced and acceptance retries the request`() = runTest {
		every { projectsRepository.getProjects() } returns emptyList()
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), null)
		} returns ServerSetupResult.TermsRequired(TermsOfServiceChallenge(text = "Legal text", version = "v1"))
		coEvery {
			accountUseCase.setupServer(any(), any(), any(), any(), "v1")
		} returns ServerSetupResult.Success

		val component = newComponent()
		component.setupServer(
			url = "example.com",
			email = "writer@example.com",
			password = "Password1!",
			create = true,
			removeLocalContent = false,
		)
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
		component.setupServer(
			url = "example.com",
			email = "writer@example.com",
			password = "Password1!",
			create = true,
			removeLocalContent = false,
		)
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
}
