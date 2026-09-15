package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore

class WearProjectsComponent(
	componentContext: ComponentContext,
	private val globalSettingsStore: GlobalSettingsStore,
) : ComponentBase(componentContext), WearProjects {

	private val _state = MutableValue(
		WearProjects.State(accountEmail = globalSettingsStore.serverSettings?.email)
	)
	override val state: Value<WearProjects.State> = _state

	override fun signOut() {
		globalSettingsStore.deleteServerSettings()
	}
}
