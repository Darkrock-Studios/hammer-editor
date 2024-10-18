package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
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

	override suspend fun createEvent(dateText: String?, contentText: String): Boolean {
		val date = if (dateText?.isNotBlank() == true) {
			dateText.trim()
		} else {
			null
		}

		val event = timeLineRepository.createEvent(
			content = contentText,
			date = date,
		)

		Napier.i { "Time Line event created! ${event.id}" }

		return true
	}

	override fun closeCreation() {
		onClose()
	}
}