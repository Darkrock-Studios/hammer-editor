package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

interface CreateTimeLineEvent {
	val state: Value<State>

	suspend fun createEvent(
		dateText: String?,
		contentText: String,
		tags: Set<String> = emptySet(),
	): TimeLineEventError

	fun closeCreation()

	data class State(
		val projectDef: ProjectDef
	)
}
