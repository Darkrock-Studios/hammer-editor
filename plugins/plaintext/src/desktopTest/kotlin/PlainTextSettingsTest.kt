import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import com.darkrockstudios.apps.hammer.plugins.plaintext.Italics
import com.darkrockstudios.apps.hammer.plugins.plaintext.ParagraphStyle
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextPlugin
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettingKeys
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals

class PlainTextSettingsTest {

	private val fileSystem = FakeFileSystem()
	private val toml = createTomlSerializer()
	private val registry = PluginRegistry(listOf(PlainTextPlugin))

	@BeforeEach
	fun setup() {
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { toml }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
		}
		startKoin { modules(listOf(base) + registry.koinModules()) }
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	private fun settings() = registry.settings(PlainTextPlugin.ID)!!.decode(PlainTextSettings.serializer())

	@Test
	fun `defaults match the settings class`() {
		assertEquals(PlainTextSettings(), settings())
	}

	@Test
	fun `changes decode into the plugin's settings`() {
		registry.settings(PlainTextPlugin.ID)!!.set(PlainTextSettingKeys.PARAGRAPHS, JsonPrimitive(ParagraphStyle.Indented.name))

		assertEquals(ParagraphStyle.Indented, settings().paragraphs)
	}

	@Test
	fun `a file saved from the settings class loads`() {
		val directory = getConfigDirectory().toPath() / PluginSettingsDatasource.PLUGINS_DIRECTORY
		fileSystem.createDirectories(directory)
		fileSystem.write(directory / "${PlainTextPlugin.ID}.toml") {
			writeUtf8(toml.encodeToString(PlainTextSettings.serializer(), PlainTextSettings(italics = Italics.Dropped)))
		}

		assertEquals(Italics.Dropped, settings().italics)
	}
}
