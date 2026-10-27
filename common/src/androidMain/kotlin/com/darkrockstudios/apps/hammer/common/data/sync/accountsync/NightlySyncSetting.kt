package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether Android runs a full account sync overnight while charging. Needs a signed-in server. */
class NightlySyncSetting(private val settings: Settings) {
	private val _enabled = MutableStateFlow(settings.getBoolean(AndroidSettingsKeys.KEY_NIGHTLY_SYNC, true))
	val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

	fun set(enabled: Boolean) {
		settings[AndroidSettingsKeys.KEY_NIGHTLY_SYNC] = enabled
		_enabled.value = enabled
	}
}
