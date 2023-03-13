package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectInject
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.github.aakira.napier.Napier

class CreateTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), CreateTimeLineEvent {

	private val timeLineRepository: TimeLineRepository by projectInject()
	private val idRepository: IdRepository by projectInject()

	private val _state = MutableValue(CreateTimeLineEvent.State(projectDef))
	override val state: Value<CreateTimeLineEvent.State> = _state

	override suspend fun createEvent(dateText: String?, contentText: String): Boolean {
		val timeline = timeLineRepository.loadTimeline()

		val id = idRepository.claimNextId()

		val date = if (dateText?.isNotBlank() == true) {
			dateText
		} else {
			null
		}
		val event = TimeLineEvent(
			id = id,
			date = date,
			content = contentText
		)

		val newEvents = timeline.events.toMutableList()
		newEvents.add(event)

		val updatedTimeline = timeline.copy(
			events = newEvents
		)

		timeLineRepository.storeTimeline(updatedTimeline)

		Napier.i { "Time Line event created! ${event.id}" }

		return true
	}

}