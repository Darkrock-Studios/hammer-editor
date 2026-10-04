package components.projectselection.accountsettings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.sandbox.NoopSandboxFileAccess
import com.darkrockstudios.apps.hammer.common.sandbox.SandboxFileAccess
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import utils.FakeAppUpdater

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopPlatformSettingsComponentTest : BaseTest() {

	private lateinit var settingsStore: GlobalSettingsStore
	private lateinit var settingsUpdates: MutableSharedFlow<GlobalSettings>
	private lateinit var appUpdater: FakeAppUpdater

	private val settings = GlobalSettings(
		projectsDirectory = "/projects",
		spellCheckSettings = SpellCheckerSettings(locale = mockk()),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		settingsStore = mockk(relaxed = true)
		settingsUpdates = MutableSharedFlow(extraBufferCapacity = 1)
		every { settingsStore.globalSettings } returns settings
		every { settingsStore.globalSettingsUpdates } returns settingsUpdates

		val projectsRepository = mockk<ProjectsRepository>()
		every { projectsRepository.getProjectsDirectory() } returns HPath("/projects", "projects", isAbsolute = true)

		appUpdater = FakeAppUpdater()

		setupKoin(
			module {
				single { settingsStore } bind GlobalSettingsStore::class
				single { projectsRepository } bind ProjectsRepository::class
				single<SandboxFileAccess> { NoopSandboxFileAccess }
				single<AppUpdater> { appUpdater }
			}
		)
	}

	private fun newComponent(): DesktopPlatformSettingsComponent {
		val lifecycle = LifecycleRegistry()
		val component = DesktopPlatformSettingsComponent(DefaultComponentContext(lifecycle = lifecycle))
		lifecycle.resume()
		return component
	}

	@Test
	fun `the update toggle is offered only on a build that can update`() = runTest {
		appUpdater.state.value = AppUpdateState.Idle
		assertTrue(newComponent().state.value.updateChecksSupported)

		appUpdater.state.value = AppUpdateState.Unsupported
		assertFalse(newComponent().state.value.updateChecksSupported)
	}

	@Test
	fun `the stored preference is mirrored into the state`() = runTest {
		val component = newComponent()
		advanceUntilIdle()
		assertTrue(component.state.value.automaticUpdateChecks)

		settingsUpdates.emit(settings.copy(automaticUpdateChecks = false))
		advanceUntilIdle()

		assertFalse(component.state.value.automaticUpdateChecks)
	}

	@Test
	fun `turning the preference off writes it to the settings`() = runTest {
		val component = newComponent()
		val update = slot<(GlobalSettings) -> GlobalSettings>()
		coEvery { settingsStore.updateSettings(capture(update)) } just Runs

		component.setAutomaticUpdateChecks(false)
		advanceUntilIdle()

		assertFalse(update.captured(settings).automaticUpdateChecks)
	}
}
