package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

interface CreateTimeLineEvent {
	val state: Value<State>

	suspend fun createEvent(dateText: String?, contentText: String): Boolean

	data class State(
		val projectDef: ProjectDef
	)
}