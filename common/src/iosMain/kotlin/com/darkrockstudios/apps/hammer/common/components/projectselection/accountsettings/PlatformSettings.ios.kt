package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import dev.icerock.moko.parcelize.Parcelize

class IosSettingsComponent(componentContext: ComponentContext): PlatformSettings,
	SavableComponent<IosSettingsComponent.PlatformState>(componentContext){

	private val _state = MutableValue(PlatformState())
	override val state: Value<PlatformState> = _state

	@Parcelize
	class PlatformState(
	) : Parcelable
}