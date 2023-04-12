package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	eventId: Int,
) : ProjectComponentBase(projectDef, componentContext), ViewTimeLineEvent {

	private val mainDispatcher by injectMainDispatcher()
	private val timeLineRepository: TimeLineRepository by projectInject()

	override val eventId: Int = eventId

	private val _state = MutableValue(ViewTimeLineEvent.State())
	override val state: Value<ViewTimeLineEvent.State> = _state

	override fun onCreate() {
		super.onCreate()

		loadEvent()
		watchTimeLine()
	}

	private fun watchTimeLine() {
		scope.launch {
			timeLineRepository.timelineFlow.collect { timeLine ->
				withContext(mainDispatcher) {
					val updatedEvent = timeLine.events.find { it.id == eventId }
					if (updatedEvent != state.value.event) {
						_state.reduce {
							it.copy(event = updatedEvent)
						}
					}
				}
			}
		}
	}

	private fun loadEvent() {
		scope.launch {
			val events = timeLineRepository.loadTimeline().events
			val event = events.find { it.id == eventId }

			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						event = event
					)
				}
			}
		}
	}

	override suspend fun updateEvent(event: TimeLineEvent): Boolean {
		return timeLineRepository.updateEvent(event)
	}
}