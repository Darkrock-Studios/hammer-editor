package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
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

class ServerSettingsDatasourceTest : BaseTest() {
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json

	@BeforeEach
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
	}

	private fun createDatasource(): ServerSettingsDatasource {
		return ServerSettingsFilesystemDatasource(
			fileSystem,
			json
		)
	}

	private fun createConfig() = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		installId = "abc123",
		bearerToken = "zxc456",
		refreshToken = "bnm789",
	)

	private fun configPath() =
		(fileSystem.workingDirectory / ServerSettingsFilesystemDatasource.SERVER_FILE_NAME).toHPath()

	private fun projectsDir() = fileSystem.workingDirectory.toHPath()

	@Test
	fun `Load Server Settings when none exists`() = runTest {
		val datasource = createDatasource()

		val loaded = datasource.loadServerSettings(projectsDir())
		assertNull(loaded)
	}

	@Test
	fun `Load Server Settings when one exists`() = runTest {
		val datasource = createDatasource()

		val serverConfig = createConfig()
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, serverConfig)

		val loaded = datasource.loadServerSettings(projectsDir())
		assertEquals(serverConfig, loaded)
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

		val serverConfig = createConfig()
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, serverConfig)

		val isSetup = datasource.serverIsSetup(projectsDir())

		assertTrue(isSetup)
	}

	@Test
	fun `Store Server Settings`() = runTest {
		val datasource = createDatasource()
		val serverConfig = createConfig()

		datasource.storeServerSettings(serverConfig, projectsDir())

		fileSystem.read(configPath().toOkioPath()) {
			val jsonStr = readUtf8()
			val storedSettings: ServerSettings = json.decodeFromString(jsonStr)

			assertEquals(serverConfig, storedSettings)
		}
	}

	@Test
	fun `Remove Server Settings when one exists`() = runTest {
		val datasource = createDatasource()

		val serverConfig = createConfig()
		fileSystem.createDirectories(projectsDir().toOkioPath())
		fileSystem.writeJson(configPath().toOkioPath(), json, serverConfig)

		datasource.removeServerSettings(projectsDir())

		assertFalse(fileSystem.exists(configPath().toOkioPath()))
	}

	@Test
	fun `Remove Server Settings when none exists`() = runTest {
		val datasource = createDatasource()
		datasource.removeServerSettings(projectsDir())
		assertFalse(fileSystem.exists(configPath().toOkioPath()))
	}
}