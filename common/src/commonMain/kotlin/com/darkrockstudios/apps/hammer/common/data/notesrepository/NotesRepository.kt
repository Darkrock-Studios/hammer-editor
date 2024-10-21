package com.darkrockstudios.apps.hammer.common.data.notesrepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class NotesRepository(
	projectDef: ProjectDef,
	private val idRepository: IdRepository,
	private val projectSynchronizer: ClientProjectSynchronizer,
	private val notesDatasource: NotesDatasource,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val notesScope = CoroutineScope(dispatcherDefault)

	private var _notes = mutableListOf<NoteContainer>()

	init {
		projectScope.scope.registerCallback(this)
		loadNotes()
	}

	fun loadNotes(onLoaded: (() -> Unit)? = null) {
		notesScope.launch {
			val notes = notesDatasource.loadNotes()
			updateNotes(notes)
			onLoaded?.invoke()
		}
	}

	/**
	 * `notesListFlow` should be used instead of this property.
	 */
	fun getNotes(): List<NoteContainer> = _notes
	fun findNoteForId(id: Int): NoteContent? {
		val foundContainer = _notes.find { it.note.id == id }
		return foundContainer?.note
	}

	private val _notesListFlow = MutableSharedFlow<List<NoteContainer>>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		replay = 1
	)
	val notesListFlow: SharedFlow<List<NoteContainer>> = _notesListFlow

	private suspend fun updateNotes(notes: List<NoteContainer>) {
		_notes = notes.toMutableList()
		_notesListFlow.emit(notes)
	}

	private suspend fun markForSync(id: Int, originalHash: String? = null) {
		if (projectSynchronizer.isServerSynchronized() && !projectSynchronizer.isEntityDirty(id)) {
			val hash = if (originalHash != null) {
				originalHash
			} else {
				val noteContainer = _notes.first { it.note.id == id }
				EntityHasher.hashNote(
					id = noteContainer.note.id,
					created = noteContainer.note.created,
					content = noteContainer.note.content,
				)
			}
			projectSynchronizer.markEntityAsDirty(id, hash)
		}
	}

	suspend fun createNote(noteText: String): CResult<NoteContent> {
		val result = validateNote(noteText)
		return if (result != NoteError.NONE) {
			CResult.failure(InvalidNote(result))
		} else {
			val newId = idRepository.claimNextId()
			val newNote = NoteContainer(
				NoteContent(
					id = newId,
					created = Clock.System.now(),
					content = noteText
				)
			)

			notesDatasource.storeNote(newNote)

			markForSync(
				id = newId,
				originalHash = ""
			)

			CResult.success(newNote.note)
		}
	}

	suspend fun deleteNote(id: Int) {
		notesDatasource.deleteNote(id)
		projectSynchronizer.recordIdDeletion(id)
	}

	suspend fun updateNote(noteContent: NoteContent, markForSync: Boolean = true) {
		notesDatasource.updateNote(noteContent)

		if (markForSync) {
			markForSync(id = noteContent.id)
		}
	}

	suspend fun reIdNote(oldId: Int, newId: Int) {
		val newNote = notesDatasource.reIdNote(oldId, newId)

		// Update the in-memory notes list
		val oldNote = getNoteById(oldId)
		val updatedNotes = getNotes().toMutableList()
		updatedNotes.indexOf(oldNote).let { index ->
			updatedNotes[index] = NoteContainer(newNote)
		}
		updateNotes(updatedNotes)
	}

	fun validateNote(noteText: String): NoteError {
		val trimmed = noteText.trim()
		return if (noteText.trim().length > MAX_NOTE_SIZE) {
			NoteError.TOO_LONG
		} else if (trimmed.isEmpty()) {
			NoteError.EMPTY
		} else {
			NoteError.NONE
		}
	}

	suspend fun getNoteById(id: Int): NoteContainer? {
		return notesListFlow.first().find { it.note.id == id }
	}

	override fun onScopeClose(scope: Scope) {
		notesScope.cancel("Closing NotesRepository")
	}

	companion object {
		const val MAX_NOTE_SIZE = 10000
	}
}
