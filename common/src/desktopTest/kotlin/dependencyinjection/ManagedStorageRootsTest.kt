package dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.managedStorageRoots
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import okio.Path.Companion.toPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertTrue

/**
 * Pins the cycle-safe roots resolution used by the production guarded FileSystem
 * binding: it must surface the configured projects directory without requiring a
 * fully built GlobalSettingsStore.
 */
class ManagedStorageRootsTest {

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `roots include cache, config, and the configured projects directory`() {
		val projectsDir = "/home/user/Documents/MyProjects"
		val settings = GlobalSettings(projectsDirectory = projectsDir)

		val koin = startKoin {
			modules(
				module {
					single<GlobalSettingsDatasource> { FixedSettingsDatasource(settings) }
				}
			)
		}.koin

		val roots = managedStorageRoots(koin)

		assertTrue(roots.any { getCacheDirectory().toPath().isWithin(it) || it == getCacheDirectory().toPath() })
		assertTrue(roots.contains(getConfigDirectory().toPath()))
		assertTrue(roots.contains(projectsDir.toPath()))
	}

	@Test
	fun `falls back to the default projects dir when nothing is registered`() {
		val koin = startKoin { modules(module {}) }.koin

		val roots = managedStorageRoots(koin)

		// Cache and config roots are always present even with no settings source.
		assertTrue(roots.contains(getCacheDirectory().toPath()))
		assertTrue(roots.contains(getConfigDirectory().toPath()))
		assertTrue(roots.contains(GlobalSettingsStore.defaultProjectDir()))
	}

	private class FixedSettingsDatasource(
		private val settings: GlobalSettings,
	) : GlobalSettingsDatasource {
		override fun loadSettings(): GlobalSettings = settings
		override fun storeSettings(settings: GlobalSettings) = Unit
	}
}
