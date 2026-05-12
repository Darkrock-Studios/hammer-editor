package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseNotesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val onShowCreate: () -> Unit,
	private val onViewNote: (Int) -> Unit,
) : SavableProjectComponentBase<BrowseNotes.State>(projectDef, componentContext), BrowseNotes {

	private val notesRepository: NotesRepository by projectInject()
	private val tagIndexService: TagIndexService by projectInject()

	private val _state =
		MutableValue(BrowseNotes.State(projectDef = projectDef, notes = emptyList()))
	override val state: Value<BrowseNotes.State> = _state
	override fun getStateSerializer() = BrowseNotes.State.serializer()

	private val _rankedTags = MutableValue<List<TagCount>>(emptyList())
	override val rankedTags: Value<List<TagCount>> = _rankedTags

	override fun onCreate() {
		super.onCreate()
		watchNotes()
		watchTags()
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

	private fun watchTags() {
		scope.launch {
			tagIndexService.tagIndex.collect {
				val ranked = tagIndexService.getRankedTags(TaggedEntityType.Note)
				withContext(dispatcherMain) {
					if (ranked != _rankedTags.value) _rankedTags.value = ranked
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