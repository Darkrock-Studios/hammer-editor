package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath

/**
 * Avoids the production [com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource],
 * whose `CONFIG_PATH` is evaluated against the global `appDirs` at class load and would otherwise
 * write into the developer's real `%APPDATA%\DarkrockStudios\hammer\...`.
 */
class InMemoryGlobalSettingsDatasource(
	initial: GlobalSettings,
) : GlobalSettingsDatasource {
	private var current: GlobalSettings = initial

	override fun loadSettings(): GlobalSettings = current

	override fun storeSettings(settings: GlobalSettings) {
		current = settings
	}
}

class InMemoryServerSettingsDatasource : ServerSettingsDatasource {
	private val store = mutableMapOf<String, ServerSettings>()

	private fun key(projectsDir: HPath): String = projectsDir.path

	override fun serverIsSetup(projectsDir: HPath): Boolean = store.containsKey(key(projectsDir))

	override fun loadServerSettings(projectsDir: HPath): ServerSettings? = store[key(projectsDir)]

	override fun storeServerSettings(settings: ServerSettings, projectsDir: HPath) {
		store[key(projectsDir)] = settings
	}

	override fun removeServerSettings(projectsDir: HPath) {
		store.remove(key(projectsDir))
	}
}
