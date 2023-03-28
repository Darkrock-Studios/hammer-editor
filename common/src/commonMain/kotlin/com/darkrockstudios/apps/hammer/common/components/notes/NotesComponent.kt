package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), Notes {

	private val notesRepository: NotesRepository by projectInject()

	private val _state = MutableValue(Notes.State(projectDef = projectDef, notes = emptyList()))
	override val state: Value<Notes.State> = _state

	override fun onCreate() {
		super.onCreate()
		watchNotes()
		notesRepository.loadNotes()
	}

	private fun watchNotes() {
		scope.launch {
			notesRepository.notesListFlow.collect { noteContainers ->
				withContext(dispatcherMain) {
					val notes = noteContainers.map { it.note }
					_state.reduce {
						it.copy(notes = notes)
					}
				}
			}
		}
	}

	override fun createNote(noteText: String): NoteError {
		val result = notesRepository.createNote(noteText)
		if (result.isSuccess) {
			dismissCreate()
			notesRepository.loadNotes()
		}

		return result
	}

	override fun deleteNote(id: Int) {
		notesRepository.deleteNote(id)
		notesRepository.loadNotes()
	}

	override fun updateNote(noteContent: NoteContent) {
		notesRepository.updateNote(noteContent)
		notesRepository.loadNotes()
	}

	override fun confirmDelete(note: NoteContent) {
		_state.reduce { it.copy(confirmDelete = note) }
	}

	override fun dismissConfirmDelete() {
		_state.reduce { it.copy(confirmDelete = null) }
	}

	override fun showCreate() {
		_state.reduce { it.copy(showCreate = true) }
	}

	override fun dismissCreate() {
		_state.reduce { it.copy(showCreate = false) }
	}
}