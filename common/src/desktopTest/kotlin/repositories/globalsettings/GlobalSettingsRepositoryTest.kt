package repositories.globalsettings

import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalSettingsRepositoryTest : BaseTest() {

	private lateinit var serverSettings: ServerSettingsDatasource
	private lateinit var globalSettings: GlobalSettingsDatasource

	@BeforeEach
	override fun setup() {
		super.setup()

		serverSettings = mockk()
		globalSettings = mockk()
	}

	private fun createDefaultRepository(): GlobalSettingsRepository {
		coEvery { globalSettings.loadSettings() } returns GlobalSettingsRepository.createDefault()
		coEvery { serverSettings.loadServerSettings(any()) } returns null

		return GlobalSettingsRepository(
			globalSettings,
			serverSettings,
		)
	}

	private fun defaultGlobalSettings() = GlobalSettingsRepository.createDefault()

	private fun projectsDir() = GlobalSettingsRepository.defaultProjectDir().toHPath()

	private fun createServerConfig() = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		installId = "abc123",
		bearerToken = "zxc456",
		refreshToken = "bnm789",
	)

	@Test
	fun `Initialize Repository, with server settings`() = runTest {
		coEvery { globalSettings.loadSettings() } returns defaultGlobalSettings()
		coEvery { serverSettings.loadServerSettings(any()) } returns createServerConfig()

		val repo = GlobalSettingsRepository(
			globalSettings,
			serverSettings,
		)

		assertEquals(defaultGlobalSettings(), repo.globalSettings)
		assertEquals(createServerConfig(), repo.serverSettings)

		repo.globalSettingsUpdates.test {
			assertEquals(defaultGlobalSettings(), awaitItem())
			cancelAndConsumeRemainingEvents()
		}

		repo.serverSettingsUpdates.test {
			assertEquals(createServerConfig(), awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `Initialize Repository, no server`() = runTest {
		val repo = createDefaultRepository()

		assertEquals(defaultGlobalSettings(), repo.globalSettings)
		assertNull(repo.serverSettings)

		repo.globalSettingsUpdates.test {
			assertEquals(defaultGlobalSettings(), awaitItem())
			cancelAndConsumeRemainingEvents()
		}

		repo.serverSettingsUpdates.test {
			assertNull(awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `Update Global Settings`() = runTest {
		coEvery { globalSettings.storeSettings(any()) } just Runs

		val updated = defaultGlobalSettings().copy(
			uiTheme = UiTheme.Light,
			automaticBackups = false,
			automaticSyncing = false,
		)

		val repo = createDefaultRepository()
		repo.updateSettings { curSettings ->
			curSettings.copy(
				uiTheme = UiTheme.Light,
				automaticBackups = false,
				automaticSyncing = false,
			)
		}

		coVerify { globalSettings.storeSettings(updated) }
		repo.globalSettingsUpdates.test {
			assertEquals(updated, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `Change projects directory, no server config in new dir`() = runTest {
		val newDirName = "NewProjectDir"
		val newProjDir = GlobalSettingsRepository.defaultProjectDir().parent!! / newDirName

		val defaultSettings = defaultGlobalSettings()
		coEvery { globalSettings.loadSettings() } returns defaultSettings
		coEvery {
			serverSettings.loadServerSettings(
				defaultSettings.projectsDirectory.toPath().toHPath()
			)
		} returns createServerConfig()
		coEvery { serverSettings.loadServerSettings(newProjDir.toHPath()) } returns null
		coEvery { globalSettings.storeSettings(any()) } just Runs

		val updated = defaultGlobalSettings().copy(
			projectsDirectory = newProjDir.toHPath().path
		)

		val repo = GlobalSettingsRepository(
			globalSettings,
			serverSettings,
		)

		assertNotNull(repo.serverSettings)

		repo.updateSettings { updated }

		coVerify { globalSettings.storeSettings(updated) }
		repo.globalSettingsUpdates.test {
			assertEquals(updated, awaitItem())
			cancelAndConsumeRemainingEvents()
		}

		assertNull(repo.serverSettings)
		repo.serverSettingsUpdates.test {
			assertNull(awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `Update Server Settings`() = runTest {
		coEvery { serverSettings.storeServerSettings(any(), any()) } just Runs

		val updated = createServerConfig().copy(
			ssl = false,
			url = "example.com",
			userId = 3,
		)

		val repo = createDefaultRepository()
		repo.updateServerSettings(updated)

		coVerify { serverSettings.storeServerSettings(updated, projectsDir()) }
		repo.serverSettingsUpdates.test {
			assertEquals(updated, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `Check if server setup when it is`() = runTest {
		coEvery { serverSettings.serverIsSetup(any()) } returns true

		val repo = createDefaultRepository()
		val isSetup = repo.serverIsSetup()

		assertTrue(isSetup)
	}

	@Test
	fun `Check if server setup when it isn't setup`() = runTest {
		coEvery { serverSettings.serverIsSetup(any()) } returns false

		val repo = createDefaultRepository()
		val isSetup = repo.serverIsSetup()

		assertFalse(isSetup)
	}

	@Test
	fun `Delete server settings successfully`() = runTest {
		coEvery { globalSettings.loadSettings() } returns GlobalSettingsRepository.createDefault()
		coEvery { serverSettings.loadServerSettings(any()) } returns createServerConfig()
		coEvery { serverSettings.removeServerSettings(any()) } just Runs

		val repo = GlobalSettingsRepository(
			globalSettings,
			serverSettings,
		)

		assertEquals(createServerConfig(), repo.serverSettings)
		repo.serverSettingsUpdates.test {
			assertNotNull(awaitItem())
		}

		repo.deleteServerSettings()

		assertNull(repo.serverSettings)
		repo.serverSettingsUpdates.test {
			assertNull(awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}
}