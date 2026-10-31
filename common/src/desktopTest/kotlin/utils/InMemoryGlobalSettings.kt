package utils

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import io.mockk.every
import io.mockk.mockk

/** Holds the settings file in memory; [stored] is what the last store wrote. */
class InMemoryGlobalSettingsDatasource(
	var stored: GlobalSettings = GlobalSettings(projectsDirectory = "/projects"),
) : GlobalSettingsDatasource {
	override fun loadSettings(): GlobalSettings = stored
	override fun storeSettings(settings: GlobalSettings) {
		stored = settings
	}
}

/** A real [GlobalSettingsStore] over [datasource], with no sync server configured. */
fun inMemoryGlobalSettingsStore(
	datasource: GlobalSettingsDatasource = InMemoryGlobalSettingsDatasource(),
): GlobalSettingsStore {
	val serverSettings = mockk<ServerSettingsDatasource> {
		every { loadServerSettings(any()) } returns null
	}
	return GlobalSettingsStore(datasource, serverSettings, mockk())
}
