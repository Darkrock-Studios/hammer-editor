package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.*
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.timeline_view_menu_delete
import com.darkrockstudios.apps.hammer.timeline_view_menu_group
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ViewTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	override val eventId: Int,
	private val onCloseEvent: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val updateShouldClose: () -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ViewTimeLineEvent {

	private val mainDispatcher by injectMainDispatcher()
	private val timeLineRepository: TimeLineRepository by projectInject()

	// A restored in-progress edit, only present after process death while editing.
	private val restoredDraft: SavedDraft? =
		stateKeeper.consume(DRAFT_KEY, SavedDraft.serializer())?.takeIf { it.isEditing }

	private val _state = MutableValue(
		ViewTimeLineEvent.State(
			tags = restoredDraft?.tags ?: emptySet(),
			isEditing = restoredDraft != null,
		)
	)
	override val state: Value<ViewTimeLineEvent.State> = _state

	private val _dateText = MutableValue(restoredDraft?.dateText ?: "")
	override val dateText: Value<String> = _dateText

	private val _contentText = MutableValue(restoredDraft?.contentText ?: "")
	override val contentText: Value<String> = _contentText

	private val backButtonHandler = BackCallback(isEnabled = false) {
		if (isEditingAndDirty()) {
			confirmDiscard()
		} else if (state.value.isEditing) {
			discardEdit()
		}
	}

	init {
		stateKeeper.register(DRAFT_KEY, SavedDraft.serializer()) {
			SavedDraft(
				contentText = _contentText.value,
				dateText = _dateText.value,
				tags = _state.value.tags,
				isEditing = _state.value.isEditing,
			)
		}
	}

	override fun onCreate() {
		super.onCreate()

		backHandler.register(backButtonHandler)
		state.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.isEditing
		}

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
							it.copy(
								event = updatedEvent,
								tags = if (it.isEditing) it.tags else (updatedEvent?.tags ?: emptySet()),
							)
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
						event = event,
						tags = if (restoredDraft != null) it.tags else event?.tags ?: emptySet(),
					)
				}
				if (restoredDraft == null) {
					_contentText.update { event?.content ?: "" }
					_dateText.update { event?.date ?: "" }
				}
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

	override fun onTagsChanged(newTags: Set<String>) {
		_state.getAndUpdate { it.copy(tags = newTags) }
		updateShouldClose()
	}

	override suspend fun removeTag(tag: String) {
		if (state.value.isEditing) return
		val event = state.value.event ?: return
		if (tag !in event.tags) return

		val updated = event.copy(tags = event.tags - tag)
		val success = timeLineRepository.updateEvent(updated)
		if (success) {
			val stored = timeLineRepository.getTimelineEvent(event.id)
			_state.getAndUpdate {
				it.copy(
					event = stored ?: updated,
					tags = stored?.tags ?: updated.tags,
				)
			}
		}
	}

	override fun showGlobalSearchForTag(tag: String) {
		onShowGlobalSearchForTag(tag)
	}

	override suspend fun storeEvent(event: TimeLineEvent): Boolean {
		val success = timeLineRepository.updateEvent(event)

		if (success) {
			val stored = timeLineRepository.getTimelineEvent(event.id)
			_state.getAndUpdate {
				it.copy(
					isEditing = false,
					event = stored ?: it.event,
					tags = stored?.tags ?: it.tags,
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
			Res.string.timeline_view_menu_delete,
			"",
		) {
			startDeleteEvent()
		}

		val menuItems = setOf(deleteEntry)
		val menu = MenuDescriptor(
			getMenuId(),
			Res.string.timeline_view_menu_group,
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
		val event = state.value.event ?: return false
		return state.value.isEditing && (
			event.content != contentText.value ||
				event.date != dateText.value ||
				event.tags != state.value.tags
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
		val event = _state.value.event
		_state.getAndUpdate {
			it.copy(
				isEditing = false,
				confirmDiscard = false,
				tags = event?.tags ?: emptySet(),
			)
		}
		_contentText.update { event?.content ?: "" }
		_dateText.update { event?.date ?: "" }
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
			closeEvent()
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
		if (state.value.isEditing) discardEdit()
		onCloseEvent()
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}

	@Serializable
	private data class SavedDraft(
		val contentText: String,
		val dateText: String,
		val tags: Set<String>,
		val isEditing: Boolean,
	)

	private companion object {
		const val DRAFT_KEY = "view-timeline-event-draft"
	}
}
