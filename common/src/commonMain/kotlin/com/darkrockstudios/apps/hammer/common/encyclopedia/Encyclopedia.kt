package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface Encyclopedia : HammerComponent {
	val state: Value<State>

	data class State(
		val projectDef: ProjectDef,
	)
}