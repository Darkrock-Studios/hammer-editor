package com.darkrockstudios.apps.hammer.common.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

interface TimeLine {

	val state: Value<State>

	fun createEvent(dateText: String?, contentText: String): Boolean
	fun updateEvent(event: TimeLineEvent)
	fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean = false): Boolean

	data class State(
		val timeLine: TimeLineContainer?
	)
}