package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	override val eventId: Int,
	private val onCloseEvent: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val updateShouldClose: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), ViewTimeLineEvent {

	private val mainDispatcher by injectMainDispatcher()
	private val timeLineRepository: TimeLineRepository by projectInject()

	private val _state = MutableValue(ViewTimeLineEvent.State())
	override val state: Value<ViewTimeLineEvent.State> = _state

	private val _dateText = MutableValue("")
	override val dateText: Value<String> = _dateText

	private val _contentText = MutableValue("")
	override val contentText: Value<String> = _contentText

	override fun onCreate() {
		super.onCreate()

		loadInitialEvent()
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

	private fun loadInitialEvent() {
		scope.launch {
			val events = timeLineRepository.timelineFlow.first().events
			val event = events.find { it.id == eventId }

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						event = event
					)
				}
				_contentText.update { event?.content ?: "" }
				_dateText.update { event?.date ?: "" }
			}
		}
	}

	private fun getMenuId(): String {
		return "view-timeline-event"
	}

	override fun onEventTextChanged(text: String) {
		_contentText.update { text }
		updateShouldClose()
	}

	override fun onDateTextChanged(text: String) {
		_dateText.update { text }
		updateShouldClose()
	}

	override suspend fun storeEvent(event: TimeLineEvent): Boolean {
		val success = timeLineRepository.updateEvent(event)

		if (success) {
			_state.getAndUpdate {
				it.copy(
					isEditing = false
				)
			}
		}

		return success
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

	override fun isEditingAndDirty(): Boolean {
		return state.value.isEditing && (
			state.value.event?.content != contentText.value ||
				state.value.event?.date != dateText.value
			)
	}

	private fun removeEntryMenu() {
		removeMenu(getMenuId())
		_state.getAndUpdate {
			it.copy(
				menuItems = emptySet()
			)
		}
	}

	override fun confirmDiscard() {
		if (isEditingAndDirty()) {
			_state.getAndUpdate {
				it.copy(
					confirmDiscard = true
				)
			}
		} else {
			discardEdit()
		}
	}

	override fun beginEdit() {
		_state.getAndUpdate {
			it.copy(
				isEditing = true
			)
		}
	}

	override fun discardEdit() {
		_state.getAndUpdate {
			it.copy(
				isEditing = false,
				confirmDiscard = false,
			)
		}
		_contentText.update { _state.value.event?.content ?: "" }
		_dateText.update { _state.value.event?.date ?: "" }
		updateShouldClose()
	}

	override fun cancelDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = false
			)
		}
	}

	override fun confirmClose() {
		if (isEditingAndDirty()) {
			_state.getAndUpdate {
				it.copy(
					confirmClose = true
				)
			}
		} else {
			onCloseEvent()
		}
	}

	override fun cancelClose() {
		_state.getAndUpdate {
			it.copy(
				confirmClose = false
			)
		}
	}

	override fun closeEvent() {
		onCloseEvent()
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}
}
