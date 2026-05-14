package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

interface ViewTimeLineEvent : TagSuggesting {
	val eventId: Int
	val state: Value<State>

	fun onEventTextChanged(text: String)
	fun onDateTextChanged(text: String)
	fun onTagsChanged(newTags: Set<String>)
	suspend fun removeTag(tag: String)
	fun showGlobalSearchForTag(tag: String)
	suspend fun storeEvent(event: TimeLineEvent): Boolean
	fun startDeleteEvent()
	fun endDeleteEvent()
	suspend fun deleteEvent()

	fun confirmDiscard()
	fun cancelDiscard()

	data class State(
		val event: TimeLineEvent? = null,
		val tags: Set<String> = emptySet(),
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val confirmDelete: Boolean = false,
		val confirmClose: Boolean = false,
		val confirmDiscard: Boolean = false,
		val isEditing: Boolean = false
	)

	val dateText: Value<String>
	val contentText: Value<String>
	fun isEditingAndDirty(): Boolean
	fun discardEdit()
	fun beginEdit()
	fun confirmClose()
	fun cancelClose()
	fun closeEvent()
}