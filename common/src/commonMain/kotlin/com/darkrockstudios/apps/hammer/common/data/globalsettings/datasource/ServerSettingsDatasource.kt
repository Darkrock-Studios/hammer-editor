package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.fileio.HPath

interface ServerSettingsDatasource {
	fun serverIsSetup(projectsDir: HPath): Boolean
	fun loadServerSettings(projectsDir: HPath): ServerSettings?
	fun storeServerSettings(settings: ServerSettings, projectsDir: HPath)
	fun removeServerSettings(projectsDir: HPath)
}