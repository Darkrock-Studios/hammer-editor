package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenDatasource.Companion.accountKey
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.PersistedServerSettings
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
	private val store = mutableMapOf<String, PersistedServerSettings>()

	private fun key(projectsDir: HPath): String = projectsDir.path

	override fun serverIsSetup(projectsDir: HPath): Boolean = store.containsKey(key(projectsDir))

	override fun loadServerSettings(projectsDir: HPath): PersistedServerSettings? = store[key(projectsDir)]

	override fun storeServerSettings(settings: PersistedServerSettings, projectsDir: HPath) {
		store[key(projectsDir)] = settings
	}

	override fun removeServerSettings(projectsDir: HPath): PersistedServerSettings? =
		store.remove(key(projectsDir))
}

class InMemoryAuthTokenDatasource : AuthTokenDatasource {
	private val tokens = mutableMapOf<String, AuthTokens>()

	override fun get(url: String, userId: Long): AuthTokens? = tokens[accountKey(url, userId)]

	override fun put(url: String, userId: Long, tokens: AuthTokens) {
		this.tokens[accountKey(url, userId)] = tokens
	}

	override fun remove(url: String, userId: Long) {
		tokens.remove(accountKey(url, userId))
	}
}
