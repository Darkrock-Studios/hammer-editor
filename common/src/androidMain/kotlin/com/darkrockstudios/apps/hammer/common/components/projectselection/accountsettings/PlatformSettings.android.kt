package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.components.SavableComponent

class AndroidPlatformSettings(componentContext: ComponentContext) :
	PlatformSettings,
	SavableComponent<AndroidPlatformSettings.PlatformState>(componentContext) {

		private val _state = MutableValue(PlatformState())
		override val state: Value<PlatformState> = _state

	fun updateKeepScreenOn(keepOn: Boolean) {
		_state.getAndUpdate { it.copy(
			keepScreenOn = keepOn
		) }
	}

	@Parcelize
	data class PlatformState(
		val keepScreenOn: Boolean = false
	) : Parcelable
}