package com.darkrockstudios.apps.hammer.common.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

interface ViewTimeLineEvent {
	val eventId: Int
	val state: Value<State>

	suspend fun updateEvent(event: TimeLineEvent): Boolean

	data class State(
		val event: TimeLineEvent? = null
	)
}