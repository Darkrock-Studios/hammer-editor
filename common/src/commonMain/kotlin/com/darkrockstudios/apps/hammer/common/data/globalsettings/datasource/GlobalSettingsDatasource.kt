package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings

interface GlobalSettingsDatasource {
	fun loadSettings(): GlobalSettings
	fun storeSettings(settings: GlobalSettings)
}