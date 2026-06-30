package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.FileAuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.toPersisted
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerSettingsDatasourceTest : BaseTest() {
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json
	private lateinit var authTokenStore: AuthTokenStore

	@BeforeEach
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
		authTokenStore = FileAuthTokenStore(fileSystem, json)
	}

	@AfterEach
	fun cleanup() {
		fileSystem.delete(FileAuthTokenStore.FILE_PATH, mustExist = false)
	}

	private fun createDatasource(): ServerSettingsDatasource {
		return ServerSettingsFilesystemDatasource(
			fileSystem,
			json,
			authTokenStore,
		)
	}

	private fun createConfig() = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		bearerToken = "zxc456",
		refreshToken = "bnm789",
	)

	private fun projectsDirPath() = fileSystem.workingDirectory / "HammerProjects"

	private fun configPath() =
		(projectsDirPath() / ServerSettingsFilesystemDatasource.SERVER_FILE_NAME).toHPath()

	private fun projectsDir() = projectsDirPath().toHPath()

	private fun readServerJson(): String = fileSystem.read(configPath().toOkioPath()) { readUtf8() }

	@Test
	fun `Load Server Settings when none exists`() = runTest {
		val datasource = createDatasource()

		val loaded = datasource.loadServerSettings(projectsDir())
		assertNull(loaded)
	}

	@Test
	fun `Store then reload round-trips tokens via the store`() = runTest {
		val datasource = createDatasource()
		val serverConfig = createConfig()

		datasource.storeServerSettings(serverConfig, projectsDir())

		val loaded = datasource.loadServerSettings(projectsDir())
		assertEquals(serverConfig, loaded)
	}

	@Test
	fun `Stored server json contains no tokens`() = runTest {
		val datasource = createDatasource()

		datasource.storeServerSettings(createConfig(), projectsDir())

		val jsonStr = readServerJson()
		assertFalse(jsonStr.contains("bearerToken"))
		assertFalse(jsonStr.contains("refreshToken"))
		assertFalse(jsonStr.contains("zxc456"))
		assertFalse(jsonStr.contains("bnm789"))
	}

	@Test
	fun `Tokens are retrievable from the store keyed by account`() = runTest {
		val datasource = createDatasource()
		val config = createConfig()

		datasource.storeServerSettings(config, projectsDir())

		val tokens = authTokenStore.get(config.url, config.userId)
		assertEquals(AuthTokens(config.bearerToken, config.refreshToken), tokens)
	}

	@Test
	fun `A different account returns no tokens`() = runTest {
		val datasource = createDatasource()
		val config = createConfig()

		datasource.storeServerSettings(config, projectsDir())

		assertNull(authTokenStore.get("other.example.com", config.userId))
		assertNull(authTokenStore.get(config.url, 999L))
	}

	@Test
	fun `Token store file lives in the config directory, not projects dir`() = runTest {
		val datasource = createDatasource()

		datasource.storeServerSettings(createConfig(), projectsDir())

		assertTrue(fileSystem.exists(FileAuthTokenStore.FILE_PATH))
		assertFalse(FileAuthTokenStore.FILE_PATH.isWithin(projectsDir().toOkioPath()))
	}

	@Test
	fun `Loading legacy server json ignores inline tokens and reads from the store`() = runTest {
		val legacy = createConfig()
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, legacy)

		// Precondition: the seeded file really does carry the inline tokens.
		assertTrue(readServerJson().contains("zxc456"))

		val datasource = createDatasource()
		val loaded = datasource.loadServerSettings(projectsDir())

		// Loading is a pure store-read: inline tokens are ignored (relocated up front by
		// migrateInlineTokens), and the file is left untouched.
		assertEquals(legacy.copy(bearerToken = null, refreshToken = null), loaded)
		assertNull(authTokenStore.get(legacy.url, legacy.userId))
		assertTrue(readServerJson().contains("zxc456"))
	}

	@Test
	fun `Loading server json when the store has no tokens yields null tokens`() = runTest {
		val tokenless = createConfig().copy(bearerToken = null, refreshToken = null)
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, tokenless.toPersisted())

		val datasource = createDatasource()
		val loaded = datasource.loadServerSettings(projectsDir())

		assertEquals(tokenless, loaded)
	}

	@Test
	fun `migrateInlineTokens relocates inline tokens into the store and rewrites the file`() = runTest {
		val legacy = createConfig()
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, legacy)

		val datasource = createDatasource()
		datasource.migrateInlineTokens(projectsDir())

		assertEquals(
			AuthTokens(legacy.bearerToken, legacy.refreshToken),
			authTokenStore.get(legacy.url, legacy.userId),
		)

		val rewritten = readServerJson()
		assertFalse(rewritten.contains("bearerToken"))
		assertFalse(rewritten.contains("refreshToken"))
		assertFalse(rewritten.contains("zxc456"))
		assertFalse(rewritten.contains("bnm789"))

		// Idempotent: a second run is a no-op and the relocated tokens still resolve.
		datasource.migrateInlineTokens(projectsDir())
		assertEquals(legacy, datasource.loadServerSettings(projectsDir()))
	}

	@Test
	fun `Check if Server is setup when none is`() = runTest {
		val datasource = createDatasource()

		val isSetup = datasource.serverIsSetup(projectsDir())

		assertFalse(isSetup)
	}

	@Test
	fun `Check if Server is setup when one is`() = runTest {
		val datasource = createDatasource()

		datasource.storeServerSettings(createConfig(), projectsDir())

		val isSetup = datasource.serverIsSetup(projectsDir())

		assertTrue(isSetup)
	}

	@Test
	fun `Remove Server Settings clears the account tokens from the store`() = runTest {
		val datasource = createDatasource()
		val config = createConfig()

		datasource.storeServerSettings(config, projectsDir())
		assertEquals(
			AuthTokens(config.bearerToken, config.refreshToken),
			authTokenStore.get(config.url, config.userId),
		)

		datasource.removeServerSettings(projectsDir())

		assertFalse(fileSystem.exists(configPath().toOkioPath()))
		assertNull(authTokenStore.get(config.url, config.userId))
	}

	@Test
	fun `Remove Server Settings when none exists`() = runTest {
		val datasource = createDatasource()
		datasource.removeServerSettings(projectsDir())
		assertFalse(fileSystem.exists(configPath().toOkioPath()))
	}
}
