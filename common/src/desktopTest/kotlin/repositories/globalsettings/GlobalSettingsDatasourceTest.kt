package repositories.globalsettings

import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class GlobalSettingsDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var languageUtil: LanguageUtil
	private lateinit var platformSpellCheckerFactory: PlatformSpellCheckerFactory

	@BeforeEach
	override fun setup() {
		super.setup()

		languageUtil = mockk(relaxed = true)
		platformSpellCheckerFactory = PlatformSpellCheckerFactory()
		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
	}

	private fun createDatasource(): GlobalSettingsDatasource {
		return GlobalSettingsFilesystemDatasource(
			fileSystem,
			toml,
			languageUtil,
			platformSpellCheckerFactory
		)
	}

	@Test
	fun `Load Global Settings when non exists`() = runTest {
		val datasource = createDatasource()
		val default = GlobalSettingsRepository.createDefault(languageUtil, platformSpellCheckerFactory)

		val loaded: GlobalSettings = datasource.loadSettings()

		assertEquals(default, loaded)
	}

	@Test
	fun `Load Global Settings when one already exists`() = runTest {
		val datasource = createDatasource()
		val settings = GlobalSettingsRepository.createDefault(languageUtil, platformSpellCheckerFactory).copy(
			uiTheme = UiTheme.Dark,
			automaticBackups = false,
			automaticSyncing = false,
		)
		fileSystem.writeToml(
			GlobalSettingsFilesystemDatasource.CONFIG_PATH,
			toml,
			settings
		)

		val loaded: GlobalSettings = datasource.loadSettings()

		assertEquals(settings, loaded)
	}

	@Test
	fun `Load when invalid one exists, default is returned`() = runTest {
		val datasource = createDatasource()
		fileSystem.write(GlobalSettingsFilesystemDatasource.CONFIG_PATH) {
			writeUtf8(".invalid-!@#$%toml")
		}

		val loaded: GlobalSettings = datasource.loadSettings()

		assertEquals(GlobalSettingsRepository.createDefault(languageUtil, platformSpellCheckerFactory), loaded)
	}

	@Test
	fun `Store Global Settings`() = runTest {
		val datasource = createDatasource()
		val newSettings = GlobalSettingsRepository.createDefault(languageUtil, platformSpellCheckerFactory).copy(
			automaticBackups = false,
			uiTheme = UiTheme.Light,
		)

		datasource.storeSettings(newSettings)

		fileSystem.read(GlobalSettingsFilesystemDatasource.CONFIG_PATH) {
			val tomlStr = readUtf8()
			val storedSettings: GlobalSettings = toml.decodeFromString(tomlStr)

			assertEquals(newSettings, storedSettings)
		}
	}
}