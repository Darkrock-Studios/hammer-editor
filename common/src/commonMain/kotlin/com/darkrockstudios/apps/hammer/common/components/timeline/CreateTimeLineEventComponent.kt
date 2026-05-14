package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.github.aakira.napier.Napier

class CreateTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val onClose: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), CreateTimeLineEvent {

	private val timeLineRepository: TimeLineRepository by projectInject()

	private val _state = MutableValue(CreateTimeLineEvent.State(projectDef))
	override val state: Value<CreateTimeLineEvent.State> = _state

	override suspend fun createEvent(
		dateText: String?,
		contentText: String,
		tags: Set<String>,
	): TimeLineEventError {
		val validation = timeLineRepository.validateTags(tags)
		if (validation != TimeLineEventError.NONE) return validation

		val date = if (dateText?.isNotBlank() == true) {
			dateText.trim()
		} else {
			null
		}

		val event = timeLineRepository.createEvent(
			content = contentText,
			date = date,
			tags = tags,
		)

		Napier.i { "Time Line event created! ${event.id}" }

		return TimeLineEventError.NONE
	}

	override fun closeCreation() {
		onClose()
	}
}
