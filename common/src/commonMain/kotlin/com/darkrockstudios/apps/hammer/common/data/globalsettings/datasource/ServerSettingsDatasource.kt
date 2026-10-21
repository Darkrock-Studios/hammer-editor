package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ServerSettingsDatasource {
	fun serverIsSetup(projectsDir: HPath): Boolean
	fun loadServerSettings(projectsDir: HPath): PersistedServerSettings?
	fun storeServerSettings(settings: PersistedServerSettings, projectsDir: HPath)

	/** Returns the account that was removed, if there was one. */
	fun removeServerSettings(projectsDir: HPath): PersistedServerSettings?
}
