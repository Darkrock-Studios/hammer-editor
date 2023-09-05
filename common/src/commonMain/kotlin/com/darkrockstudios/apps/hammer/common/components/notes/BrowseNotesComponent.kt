package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseNotesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val onShowCreate: () -> Unit,
	private val onViewNote: (Int) -> Unit,
) : SavableProjectComponentBase<BrowseNotes.State>(projectDef, componentContext), BrowseNotes {

	private val notesRepository: NotesRepository by projectInject()

	private val _state =
		MutableValue(BrowseNotes.State(projectDef = projectDef, notes = emptyList()))
	override val state: Value<BrowseNotes.State> = _state

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
						.sortedByDescending { it.created }
					_state.getAndUpdate {
						it.copy(notes = notes)
					}
				}
			}
		}
	}

	override fun viewNote(noteId: Int) {
		onViewNote(noteId)
	}

	override fun showCreate() {
		onShowCreate()
	}
}