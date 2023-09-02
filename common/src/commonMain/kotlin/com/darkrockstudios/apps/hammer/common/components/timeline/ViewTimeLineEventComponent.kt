package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	eventId: Int,
	private val closeEvent: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
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
						_state.getAndUpdate {
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
				_state.getAndUpdate {
					it.copy(
						event = event
					)
				}
			}
		}
	}

	private fun getMenuId(): String {
		return "view-timeline-event"
	}

	override suspend fun updateEvent(event: TimeLineEvent): Boolean {
		return timeLineRepository.updateEvent(event)
	}

	override fun startDeleteEvent() {
		_state.getAndUpdate {
			it.copy(
				confirmDelete = true
			)
		}
	}

	override fun endDeleteEvent() {
		_state.getAndUpdate {
			it.copy(
				confirmDelete = false
			)
		}
	}

	override suspend fun deleteEvent() {
		val event = state.value.event
		if (event != null) {
			timeLineRepository.deleteEvent(event)
			endDeleteEvent()
			closeEvent()
		} else {
			Napier.w("Failed to delete event, none loaded")
		}
	}

	private fun addEntryMenu() {
		val deleteEntry = MenuItemDescriptor(
			"view-timeline-event-delete",
			MR.strings.encyclopedia_entry_menu_delete,
			"",
		) {
			startDeleteEvent()
		}

		val menuItems = setOf(deleteEntry)
		val menu = MenuDescriptor(
			getMenuId(),
			MR.strings.timeline_view_menu_group,
			menuItems.toList()
		)
		addMenu(menu)
		_state.getAndUpdate {
			it.copy(
				menuItems = menuItems
			)
		}
	}

	private fun removeEntryMenu() {
		removeMenu(getMenuId())
		_state.getAndUpdate {
			it.copy(
				menuItems = emptySet()
			)
		}
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}
}