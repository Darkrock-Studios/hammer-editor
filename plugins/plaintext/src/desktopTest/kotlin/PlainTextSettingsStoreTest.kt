import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import com.darkrockstudios.apps.hammer.plugins.plaintext.Italics
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettings
import com.darkrockstudios.apps.hammer.plugins.plaintext.PlainTextSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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

class PlainTextSettingsStoreTest {

	private val datasource = PluginSettingsDatasource(FakeFileSystem(), createTomlSerializer(), "/config/plugins".toPath())

	@BeforeEach
	fun setup() {
		startKoin { modules(module { single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined } }) }
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `updates are published and survive a reload`() = runTest {
		val store = PlainTextSettingsStore(datasource)
		assertEquals(PlainTextSettings(), store.settings.value)

		store.update { it.copy(italics = Italics.Dropped) }

		assertEquals(Italics.Dropped, store.settings.value.italics)
		assertEquals(Italics.Dropped, PlainTextSettingsStore(datasource).settings.value.italics)
	}
}
