package plugin

import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PluginSettingsDatasourceTest {

	@Serializable
	data class Settings(val enabled: Boolean = false, val name: String = "")

	private val fileSystem = FakeFileSystem()
	private val pluginsDir = "/config/plugins".toPath()
	private val datasource = PluginSettingsDatasource(fileSystem, createTomlSerializer(), pluginsDir)

	@Test
	fun `missing settings load as the default`() {
		assertEquals(Settings(), datasource.load("example", Settings.serializer()) { Settings() })
	}

	@Test
	fun `stored settings round trip in the plugin's own file`() {
		val settings = Settings(enabled = true, name = "Jane")

		datasource.store("example", Settings.serializer(), settings)

		assertEquals(settings, datasource.load("example", Settings.serializer()) { Settings() })
		assertEquals(listOf(pluginsDir / "example.toml"), fileSystem.list(pluginsDir))
	}

	@Test
	fun `an unreadable file loads as the default`() {
		fileSystem.createDirectories(pluginsDir)
		fileSystem.write(pluginsDir / "example.toml") { writeUtf8("enabled = \"not a boolean") }

		assertEquals(Settings(), datasource.load("example", Settings.serializer()) { Settings() })
	}
}
