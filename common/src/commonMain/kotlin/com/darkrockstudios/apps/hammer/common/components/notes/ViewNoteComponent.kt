package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.*
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.notes_menu_delete
import com.darkrockstudios.apps.hammer.notes_menu_group
import io.github.aakira.napier.Napier

class ViewNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val noteId: Int,
	private val dismissView: () -> Unit,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ViewNote {

	private val notesRepository: NotesRepository by projectInject()

	private val _state = MutableValue(ViewNote.State(projectDef = projectDef))
	override val state: Value<ViewNote.State> = _state

	private val _noteText = MutableValue("")
	override val noteText: Value<String> = _noteText

	private val backButtonHandler = BackCallback(isEnabled = false) {
		if (isEditingAndDirty()) {
			confirmDiscard()
		} else if (state.value.isEditing) {
			discardEdit()
		}
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		state.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.isEditing
		}

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
		if (isEditingAndDirty()) {
			_state.getAndUpdate {
				it.copy(
					confirmClose = true
				)
			}
		} else {
			dismissView()
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

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}

	private val menuId = "view-note"
	private fun addEntryMenu() {
		val deleteEntry = MenuItemDescriptor(
			"view-note-delete",
			Res.string.notes_menu_delete,
			"",
		) {
			confirmDelete()
		}

		val menuItems = setOf(deleteEntry)
		val menu = MenuDescriptor(
			menuId,
			Res.string.notes_menu_group,
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
		removeMenu(menuId)
		_state.getAndUpdate {
			it.copy(
				menuItems = emptySet()
			)
		}
	}
}