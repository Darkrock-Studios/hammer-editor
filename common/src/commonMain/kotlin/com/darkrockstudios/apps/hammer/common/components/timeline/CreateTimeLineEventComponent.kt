package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.*
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable

class CreateTimeLineEventComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val onClose: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), CreateTimeLineEvent {

	private val timeLineRepository: TimeLineRepository by projectInject()

	private val _state = MutableValue(CreateTimeLineEvent.State(projectDef))
	override val state: Value<CreateTimeLineEvent.State> = _state

	private val _contentText = MutableValue(
		stateKeeper.consume(DRAFT_KEY, SavedDraft.serializer())?.contentText ?: ""
	)
	override val contentText: Value<String> = _contentText

	private val backButtonHandler = BackCallback(isEnabled = false) {
		confirmDiscard()
	}

	init {
		stateKeeper.register(DRAFT_KEY, SavedDraft.serializer()) {
			SavedDraft(contentText = _contentText.value)
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		contentText.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.isNotBlank()
		}

		watchSpellCheckAllowed { allowed ->
			_state.getAndUpdate { it.copy(spellCheckAllowed = allowed) }
		}
	}

	override fun onContentChanged(newText: String) {
		_contentText.update { newText }
	}

	override fun clearContent() {
		_contentText.update { "" }
	}

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

	override fun confirmDiscard() {
		_state.getAndUpdate { it.copy(confirmDiscard = true) }
	}

	override fun cancelDiscard() {
		_state.getAndUpdate { it.copy(confirmDiscard = false) }
	}

	override fun closeCreation() {
		if (contentText.value.isNotBlank()) {
			confirmDiscard()
		} else {
			onClose()
		}
	}

	@Serializable
	private data class SavedDraft(val contentText: String)

	private companion object {
		const val DRAFT_KEY = "create-timeline-event-draft"
	}
}
