package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import com.russhwolf.settings.boolean
import org.koin.core.component.inject
import kotlinx.serialization.Serializable

class AndroidPlatformSettings(componentContext: ComponentContext) :
	PlatformSettings,
	SavableComponent<AndroidPlatformSettings.PlatformState>(componentContext) {

	private val _state = MutableValue(PlatformState())
	override val state: Value<PlatformState> = _state
	override fun getStateSerializer() = PlatformState.serializer()

	private val settings: Settings by inject()

	init {
		val initialSetting = settings.getBoolean(AndroidSettingsKeys.KEY_SCREEN_ON, false)
		_state.getAndUpdate {
			it.copy(
				keepScreenOn = initialSetting
			)
		}
	}

	fun updateKeepScreenOn(keepOn: Boolean) {
		settings.boolean(AndroidSettingsKeys.KEY_SCREEN_ON, keepOn)

		_state.getAndUpdate {
			it.copy(
				keepScreenOn = keepOn
			)
		}
	}

	@Serializable
	data class PlatformState(
		val keepScreenOn: Boolean = false
	)
}