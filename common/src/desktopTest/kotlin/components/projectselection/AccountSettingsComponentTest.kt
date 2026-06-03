package components.projectselection

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

	private lateinit var globalSettingsRepository: GlobalSettingsRepository
	private lateinit var globalSettingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var serverSettingsUpdates: SharedFlow<ServerSettings?>

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

		globalSettingsRepository = mockk(relaxed = true)
		globalSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsRepository.globalSettingsUpdates } returns globalSettingsUpdates
		every { globalSettingsRepository.globalSettings } answers { globalSettings }

		serverSettingsUpdates = MutableSharedFlow(replay = 1)
		every { globalSettingsRepository.serverSettingsUpdates } returns serverSettingsUpdates

		val spellCheckerFactory = mockk<PlatformSpellCheckerFactory>(relaxed = true)
		every { spellCheckerFactory.availableLocales() } returns emptyList()

		val testModule = module {
			single { globalSettingsRepository } bind GlobalSettingsRepository::class
			single { mockk<ExampleProjectRepository>(relaxed = true) } bind ExampleProjectRepository::class
			single { mockk<AccountUseCase>(relaxed = true) } bind AccountUseCase::class
			single { mockk<ProjectsRepository>(relaxed = true) } bind ProjectsRepository::class
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
}
