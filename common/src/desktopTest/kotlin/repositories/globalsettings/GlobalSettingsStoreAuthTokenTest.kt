package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.FileAuthTokenDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.toPersisted
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tokens live apart from the account in `server.json`; the store joins the two. */
class GlobalSettingsStoreAuthTokenTest : BaseTest() {
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json
	private lateinit var authTokenDatasource: AuthTokenDatasource
	private lateinit var globalSettingsDatasource: GlobalSettingsDatasource

	private val projectsDir = GlobalSettingsStore.defaultProjectDir()
	private val serverJson = projectsDir / ServerSettingsFilesystemDatasource.SERVER_FILE_NAME

	@BeforeEach
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
		authTokenDatasource = FileAuthTokenDatasource(fileSystem, json)
		globalSettingsDatasource = mockk()
		every { globalSettingsDatasource.loadSettings() } returns
			GlobalSettings(projectsDirectory = projectsDir.toString())
	}

	private fun createStore() = GlobalSettingsStore(
		globalSettingsDatasource,
		ServerSettingsFilesystemDatasource(fileSystem, json),
		authTokenDatasource,
	)

	private fun createConfig() = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		bearerToken = "zxc456",
		refreshToken = "bnm789",
	)

	private fun readServerJson(): String = fileSystem.read(serverJson) { readUtf8() }

	@Test
	fun `Stored settings reload with their tokens`() = runTest {
		val config = createConfig()

		createStore().updateServerSettings(config)

		assertEquals(config, createStore().serverSettings)
	}

	@Test
	fun `Tokens are stored per account, outside server json`() = runTest {
		val config = createConfig()

		createStore().updateServerSettings(config)

		val serverJsonText = readServerJson()
		assertFalse(serverJsonText.contains("zxc456"))
		assertFalse(serverJsonText.contains("bnm789"))
		assertEquals(
			AuthTokens(config.bearerToken, config.refreshToken),
			authTokenDatasource.get(config.url, config.userId),
		)
		assertNull(authTokenDatasource.get("other.example.com", config.userId))
		assertNull(authTokenDatasource.get(config.url, 999L))
		assertFalse(FileAuthTokenDatasource.FILE_PATH.isWithin(projectsDir))
	}

	@Test
	fun `Inline tokens in a legacy server json are ignored and left in place`() = runTest {
		val legacy = createConfig()
		fileSystem.createDirectories(projectsDir)
		fileSystem.writeJson(serverJson, json, legacy)

		val loaded = createStore().serverSettings

		assertEquals(legacy.copy(bearerToken = null, refreshToken = null), loaded)
		assertNull(authTokenDatasource.get(legacy.url, legacy.userId))
		assertTrue(readServerJson().contains("zxc456"))
	}

	@Test
	fun `An account with no stored tokens loads with null tokens`() = runTest {
		val tokenless = createConfig().copy(bearerToken = null, refreshToken = null)
		fileSystem.createDirectories(projectsDir)
		fileSystem.writeJson(serverJson, json, tokenless.toPersisted())

		assertEquals(tokenless, createStore().serverSettings)
	}

	@Test
	fun `Deleting server settings removes the account's tokens`() = runTest {
		val config = createConfig()
		val store = createStore()
		store.updateServerSettings(config)

		store.deleteServerSettings()

		assertFalse(fileSystem.exists(serverJson))
		assertNull(authTokenDatasource.get(config.url, config.userId))
	}
}
