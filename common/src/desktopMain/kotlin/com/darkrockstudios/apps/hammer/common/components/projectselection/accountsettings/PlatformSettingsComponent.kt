package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import kotlinx.serialization.Serializable

class DesktopSettingsComponent(componentContext: ComponentContext): PlatformSettings,
	SavableComponent<DesktopSettingsComponent.PlatformState>(componentContext){

	private val _state = MutableValue(PlatformState())
	override val state: Value<PlatformState> = _state
	override fun getStateSerializer() = PlatformState.serializer()

	@Serializable
	class PlatformState()
}