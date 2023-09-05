package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject

class CreateNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissCreate: () -> Unit,
	private val updateShouldClose: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), CreateNote {

	private val _state = MutableValue(CreateNote.State())
	override val state: Value<CreateNote.State> = _state

	private val _noteText = MutableValue("")
	override val noteText: Value<String> = _noteText

	private val notesRepository: NotesRepository by projectInject()

	private val backButtonHandler = BackCallback {
		if (noteText.value.isNotBlank()) {
			confirmDiscard()
		} else {
			dismissCreate()
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)
	}

	override fun onTextChanged(newText: String) {
		_noteText.update { newText }
		updateShouldClose()
	}

	override fun clearText() {
		_noteText.update { "" }
		updateShouldClose()
	}

	override suspend fun createNote(noteText: String): NoteError {
		val result = notesRepository.createNote(noteText)
		if (result.isSuccess) {
			dismissCreate()
			notesRepository.loadNotes()
		}

		return result
	}

	override fun confirmDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = true
			)
		}
	}

	override fun cancelDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = false
			)
		}
	}

	override fun closeCreate() {
		if (noteText.value.isNotBlank()) {
			confirmDiscard()
		} else {
			dismissCreate()
		}
	}
}