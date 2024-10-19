package com.darkrockstudios.apps.hammer.common.data.notesrepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

abstract class NotesRepository(
	protected val projectDef: ProjectDef,
	protected val idRepository: IdRepository,
	protected val projectSynchronizer: ClientProjectSynchronizer
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val notesScope = CoroutineScope(dispatcherDefault)

	private var _notes = mutableListOf<NoteContainer>()

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

	protected suspend fun updateNotes(notes: List<NoteContainer>) {
		_notes = notes.toMutableList()
		_notesListFlow.emit(notes)
	}

	protected suspend fun markForSync(id: Int, originalHash: String? = null) {
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

	abstract fun getNotesDirectory(): HPath
	abstract fun getNotePath(id: Int): HPath
	abstract fun loadNotes(onLoaded: (() -> Unit)? = null)
	abstract suspend fun createNote(noteText: String): NoteError
	abstract suspend fun deleteNote(id: Int)
	abstract suspend fun updateNote(noteContent: NoteContent, markForSync: Boolean = true)
	abstract suspend fun reIdNote(oldId: Int, newId: Int)

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

	abstract suspend fun getNoteFromId(id: Int): NoteContainer?

	override fun onScopeClose(scope: Scope) {
		notesScope.cancel("Closing NotesRepository")
	}

	companion object {
		val NOTE_FILENAME_PATTERN = Regex("""note-(\d+)\.toml""")
		const val NOTES_FILENAME_EXTENSION = ".toml"
		const val NOTES_DIRECTORY = "notes"
		const val MAX_NOTE_SIZE = 10000

		fun getNoteFilenameFromId(id: Int): String {
			return "note-$id.toml"
		}

		fun getNoteIdFromFilename(fileName: String): Int {
			val captures = NOTE_FILENAME_PATTERN.matchEntire(fileName)
				?: throw IllegalStateException("Note filename was bad: $fileName")
			try {
				val sceneId = captures.groupValues[1].toInt()
				return sceneId
			} catch (e: NumberFormatException) {
				throw InvalidSceneFilename("Number format exception", fileName)
			} catch (e: IllegalStateException) {
				throw InvalidSceneFilename("Invalid filename", fileName)
			}
		}
	}
}


fun Sequence<HPath>.filterNotesPaths() = filter {
	!it.name.startsWith(".") && NotesRepository.NOTE_FILENAME_PATTERN.matches(it.name)
}.sortedBy { it.name }