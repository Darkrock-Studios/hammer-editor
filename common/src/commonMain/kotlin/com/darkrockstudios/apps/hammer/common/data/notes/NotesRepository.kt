package com.darkrockstudios.apps.hammer.common.data.notes

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.notes.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.Closeable

abstract class NotesRepository(
	protected val projectDef: ProjectDef,
	protected val idRepository: IdRepository
) : Closeable {
	protected val notesScope = CoroutineScope(defaultDispatcher)

	private val _notesListFlow = MutableSharedFlow<List<NoteContainer>>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val notesListFlow: SharedFlow<List<NoteContainer>> = _notesListFlow

	protected suspend fun updateNotes(notes: List<NoteContainer>) {
		_notesListFlow.emit(notes)
	}

	abstract fun getNotesDirectory(): HPath
	abstract fun getNotePath(id: Int): HPath
	abstract fun loadNotes()
	abstract fun createNote(noteText: String): NoteError

	fun validateNote(noteText: String): NoteError {
		return if (noteText.trim().length > MAX_NOTE_SIZE) {
			NoteError.TOO_LONG
		} else {
			NoteError.NONE
		}
	}

	override fun close() {
		notesScope.cancel("Closing NotesRepository")
	}

	companion object {
		val NOTE_FILENAME_PATTERN = Regex("""(\d+)\.toml""")
		const val NOTES_FILENAME_EXTENSION = ".toml"
		const val NOTES_DIRECTORY = "notes"
		const val MAX_NOTE_SIZE = 20

		fun getNoteFilenameFromId(id: Int): String {
			return "$id.toml"
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