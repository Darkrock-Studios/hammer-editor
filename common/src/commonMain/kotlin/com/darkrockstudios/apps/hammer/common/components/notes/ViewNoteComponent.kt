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
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.notes_menu_delete
import com.darkrockstudios.apps.hammer.notes_menu_group
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ViewNoteComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val noteId: Int,
	private val dismissView: () -> Unit,
	private val updateShouldClose: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ViewNote {

	private val notesRepository: NotesRepository by projectInject()

	// A restored in-progress edit, only present after process death while editing.
	private val restoredDraft: SavedDraft? =
		stateKeeper.consume(DRAFT_KEY, SavedDraft.serializer())?.takeIf { it.isEditing }

	private val _state = MutableValue(
		ViewNote.State(
			projectDef = projectDef,
			tags = restoredDraft?.tags ?: emptySet(),
			isEditing = restoredDraft != null,
		)
	)
	override val state: Value<ViewNote.State> = _state

	private val _noteText = MutableValue(restoredDraft?.noteText ?: "")
	override val noteText: Value<String> = _noteText

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
				noteText = _noteText.value,
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

		loadInitialContent()
	}

	override fun onContentChanged(newContent: String) {
		_noteText.update { newContent }
		updateShouldClose()
	}

	override fun onTagsChanged(newTags: Set<String>) {
		_state.getAndUpdate { it.copy(tags = newTags) }
		updateShouldClose()
	}

	override suspend fun removeTag(tag: String) = withContext(dispatcherDefault) {
		if (state.value.isEditing) return@withContext
		val note = state.value.note ?: return@withContext
		if (tag !in note.tags) return@withContext

		val updatedNote = note.copy(tags = note.tags - tag)
		notesRepository.updateNote(updatedNote)
		notesRepository.loadNotes()

		_state.getAndUpdate { it.copy(note = updatedNote, tags = updatedNote.tags) }
	}

	override fun showGlobalSearchForTag(tag: String) {
		onShowGlobalSearchForTag(tag)
	}

	override suspend fun storeNoteUpdate() = withContext(dispatcherDefault) {
		val note = state.value.note
		if (note != null) {
			val updatedNote = note.copy(
				content = noteText.value,
				tags = state.value.tags,
			)
			notesRepository.updateNote(updatedNote)
			notesRepository.loadNotes()

			_state.getAndUpdate {
				it.copy(
					note = updatedNote,
					tags = updatedNote.tags,
					isEditing = false
				)
			}

			updateShouldClose()
		} else {
			Napier.w("Failed to update note content! Not was null")
		}
	}

	override suspend fun deleteNote(id: Int) = withContext(dispatcherDefault) {
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
		if (state.value.isEditing) discardEdit()
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
		val note = state.value.note ?: return false
		return state.value.isEditing &&
			(note.content != noteText.value || note.tags != state.value.tags)
	}

	override fun discardEdit() {
		val note = _state.value.note
		_state.getAndUpdate {
			it.copy(
				isEditing = false,
				tags = note?.tags ?: emptySet(),
			)
		}
		_noteText.update { note?.content ?: "" }
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
			closeNote()
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
					applyLoadedNote(note)
				} else {
					error("Failed to load note: $noteId")
				}
			}
		} else {
			applyLoadedNote(note)
		}
	}

	// Attach the loaded note. When restoring an in-progress edit across process
	// death, keep the restored text/tags rather than overwriting with stored values.
	private fun applyLoadedNote(note: NoteContent?) {
		_state.getAndUpdate {
			it.copy(note = note, tags = if (restoredDraft != null) it.tags else note?.tags ?: emptySet())
		}
		if (restoredDraft == null) _noteText.update { note?.content ?: "" }
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

	@Serializable
	private data class SavedDraft(
		val noteText: String,
		val tags: Set<String>,
		val isEditing: Boolean,
	)

	private companion object {
		const val DRAFT_KEY = "view-note-draft"
	}
}