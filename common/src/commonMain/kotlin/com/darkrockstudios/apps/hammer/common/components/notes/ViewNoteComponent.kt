package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import io.github.aakira.napier.Napier

class ViewNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val noteId: Int,
	private val dismissView: () -> Unit,
	private val updateShouldClose: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), ViewNote {

	private val notesRepository: NotesRepository by projectInject()

	private val _state = MutableValue(ViewNote.State(projectDef = projectDef))
	override val state: Value<ViewNote.State> = _state

	private val _noteText = MutableValue("")
	override val noteText: Value<String> = _noteText

	private val backButtonHandler = BackCallback {
		if (isEditingAndDirty()) {
			confirmDiscard()
		} else if (state.value.isEditing) {
			discardEdit()
		} else {
			dismissView()
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		loadInitialContent()
	}

	override fun onContentChanged(newContent: String) {
		_noteText.update { newContent }
		updateShouldClose()
	}

	override suspend fun storeNoteUpdate() {
		val note = state.value.note
		if (note != null) {
			val updatedNote = note.copy(
				content = noteText.value
			)
			notesRepository.updateNote(updatedNote)
			notesRepository.loadNotes()

			_state.getAndUpdate {
				it.copy(
					note = updatedNote,
					isEditing = false
				)
			}

			updateShouldClose()
		} else {
			Napier.w("Failed to update note content! Not was null")
		}
	}

	override suspend fun deleteNote(id: Int) {
		notesRepository.deleteNote(id)
		notesRepository.loadNotes()
		dismissView()
	}

	override fun confirmDelete() {
		_state.getAndUpdate { it.copy(confirmDelete = true) }
	}

	override fun dismissConfirmDelete() {
		_state.getAndUpdate { it.copy(confirmDelete = false) }
	}

	override fun closeNote() {
		dismissView()
	}

	override fun beginEdit() {
		_state.getAndUpdate {
			it.copy(
				isEditing = true
			)
		}
	}

	override fun isEditingAndDirty(): Boolean {
		return state.value.isEditing && (state.value.note?.content != noteText.value)
	}

	override fun discardEdit() {
		_state.getAndUpdate {
			it.copy(
				isEditing = false
			)
		}
		_noteText.update { _state.value.note?.content ?: "" }
		updateShouldClose()
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

	override fun cancelDiscard() {
		_state.getAndUpdate {
			it.copy(
				confirmDiscard = false
			)
		}
	}

	override fun confirmClose() {
		_state.getAndUpdate {
			it.copy(
				confirmClose = true
			)
		}
	}

	override fun cancelClose() {
		_state.getAndUpdate {
			it.copy(
				confirmClose = false
			)
		}
	}

	private fun loadInitialContent() {
		var note = notesRepository.findNoteForId(noteId)
		if (note == null) {
			notesRepository.loadNotes {
				note = notesRepository.findNoteForId(noteId)
				if (note != null) {
					_state.getAndUpdate { it.copy(note = note) }
					_noteText.update { note?.content ?: "" }
				} else {
					error("Failed to load note: $noteId")
				}
			}
		} else {
			_state.getAndUpdate { it.copy(note = note) }
			_noteText.update { note?.content ?: "" }
		}
	}
}