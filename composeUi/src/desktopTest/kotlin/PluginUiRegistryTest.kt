import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUiRegistry
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class PluginUiRegistryTest {

	@After
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	/** A plugin with one toggle, and a check when [checks]; each call makes its check anew. */
	private class Plugin(override val id: String, private val checks: Boolean) : ClientPlugin {
		override fun settings() = listOf(SettingDeclaration.Toggle("on", "On", defaultValue = true))

		override fun textDiagnostics() =
			if (checks) listOf(TextDiagnosticsProvider("Check") { paragraphs, _ -> paragraphs.map { emptyList() } }) else emptyList()
	}

	@Test
	fun `a setting of a plugin with checks makes its checks anew, and another plugin's does not`() = runTest {
		val registry = PluginRegistry()
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { FakeFileSystem() }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
			)
		}
		registry.add(Plugin("checker", checks = true))
		registry.add(Plugin("other", checks = false))

		val emitted = mutableListOf<List<TextDiagnosticsProvider>>()
		val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
			PluginUiRegistry(registry).textDiagnosticsFlow().collect { emitted += it }
		}
		assertEquals(1, emitted.size)

		registry.settings("other")!!.set("on", JsonPrimitive(false))
		assertEquals(1, emitted.size)

		registry.settings("checker")!!.set("on", JsonPrimitive(false))
		assertEquals(2, emitted.size)
		assertNotSame(emitted[0].single(), emitted[1].single())
		collecting.cancel()
	}
}
