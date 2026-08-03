package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

interface CreateTimeLineEvent : TagSuggesting {
	val state: Value<State>
	val contentText: Value<String>

	suspend fun createEvent(
		dateText: String?,
		contentText: String,
		tags: Set<String> = emptySet(),
	): TimeLineEventError

	fun closeCreation()
	fun confirmDiscard()
	fun cancelDiscard()
	fun onContentChanged(newText: String)
	fun clearContent()

	data class State(
		val projectDef: ProjectDef,
		val confirmDiscard: Boolean = false,
		val spellCheckAllowed: Boolean = true,
	)
}
