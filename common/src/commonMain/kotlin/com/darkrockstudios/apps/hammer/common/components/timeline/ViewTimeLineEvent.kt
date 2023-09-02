package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

interface ViewTimeLineEvent {
	val eventId: Int
	val state: Value<State>

	suspend fun updateEvent(event: TimeLineEvent): Boolean
	fun startDeleteEvent()
	fun endDeleteEvent()
	suspend fun deleteEvent()

	data class State(
		val event: TimeLineEvent? = null,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val confirmDelete: Boolean = false
	)
}