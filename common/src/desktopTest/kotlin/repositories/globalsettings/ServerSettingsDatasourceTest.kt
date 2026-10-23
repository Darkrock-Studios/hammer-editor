package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.PersistedServerSettings
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
		return ServerSettingsFilesystemDatasource(fileSystem, json)
	}

	private fun createAccount() = PersistedServerSettings(
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
	)

	private fun projectsDirPath() = fileSystem.workingDirectory / "HammerProjects"

	private fun configPath() =
		(projectsDirPath() / ServerSettingsFilesystemDatasource.SERVER_FILE_NAME).toHPath()

	private fun projectsDir() = projectsDirPath().toHPath()

	private fun writeServerJson(text: String) {
		fileSystem.createDirectories(projectsDirPath())
		fileSystem.write(configPath().toOkioPath()) { writeUtf8(text) }
	}

	@Test
	fun `Load Server Settings when none exists`() = runTest {
		val datasource = createDatasource()

		val loaded = datasource.loadServerSettings(projectsDir())
		assertNull(loaded)
	}

	@Test
	fun `Store then reload round-trips the account`() = runTest {
		val datasource = createDatasource()
		val account = createAccount()

		datasource.storeServerSettings(account, projectsDir())

		assertEquals(account, datasource.loadServerSettings(projectsDir()))
	}

	@Test
	fun `A plaintext server round-trips as plaintext`() = runTest {
		val datasource = createDatasource()
		val plaintext = createAccount().copy(url = "192.168.1.50:8080", plaintext = true)

		datasource.storeServerSettings(plaintext, projectsDir())

		assertEquals(plaintext, datasource.loadServerSettings(projectsDir()))
	}

	@Test
	fun `A server json written before plaintext support loads as https`() = runTest {
		writeServerJson("""{"url":"hammer.ink","email":"test@example.com","userId":1}""")

		val loaded = createDatasource().loadServerSettings(projectsDir())

		assertFalse(loaded!!.plaintext)
	}

	@Test
	fun `A legacy ssl false key still loads as https`() = runTest {
		writeServerJson("""{"ssl":false,"url":"hammer.ink","email":"test@example.com","userId":1}""")

		val loaded = createDatasource().loadServerSettings(projectsDir())

		assertFalse(loaded!!.plaintext)
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

		datasource.storeServerSettings(createAccount(), projectsDir())

		val isSetup = datasource.serverIsSetup(projectsDir())

		assertTrue(isSetup)
	}

	@Test
	fun `Remove Server Settings deletes the file and returns the account`() = runTest {
		val datasource = createDatasource()
		val account = createAccount()

		datasource.storeServerSettings(account, projectsDir())

		assertEquals(account, datasource.removeServerSettings(projectsDir()))
		assertFalse(fileSystem.exists(configPath().toOkioPath()))
	}

	@Test
	fun `Remove Server Settings when none exists`() = runTest {
		val datasource = createDatasource()

		assertNull(datasource.removeServerSettings(projectsDir()))
		assertFalse(fileSystem.exists(configPath().toOkioPath()))
	}
}
