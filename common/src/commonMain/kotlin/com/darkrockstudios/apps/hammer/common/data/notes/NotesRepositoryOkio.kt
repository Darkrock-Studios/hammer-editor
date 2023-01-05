package com.darkrockstudios.apps.hammer.common.data.notes

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.notes.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notes.note.NoteContent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path

class NotesRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml
) : NotesRepository(projectDef, idRepository) {

	override fun getNotesDirectory() = getNotesDirectory(projectDef, fileSystem)

	override fun getNotePath(id: Int): HPath {
		val dir = getNotesDirectory().toOkioPath()
		val path = dir / getNoteFilenameFromId(id)
		return path.toHPath()
	}

	override fun loadNotes() {
		notesScope.launch {
			val dir = getNotesDirectory().toOkioPath()
			val files = fileSystem.listRecursively(dir)
			val noteFiles = files.filterNotePathsOkio()

			val notes = noteFiles.map { path -> loadNote(path) }.toList()
			updateNotes(notes)
		}
	}

	override fun createNote(noteText: String): NoteError {
		val result = validateNote(noteText)
		return if (result != NoteError.NONE) {
			result
		} else {

			val newId = idRepository.claimNextSceneId()
			val newNote = NoteContainer(
				NoteContent(
					id = newId.toLong(),
					created = Clock.System.now(),
					content = noteText
				)
			)

			val path = getNotePath(newId).toOkioPath()
			val noteToml = toml.encodeToString(newNote)
			fileSystem.write(path, mustCreate = true) {
				writeUtf8(noteToml)
			}

			NoteError.NONE
		}
	}

	override fun deleteNote(id: Int) {
		val path = getNotePath(id).toOkioPath()
		fileSystem.delete(path, true)
	}

	override fun updateNote(noteContent: NoteContent) {
		val note = NoteContainer(noteContent)
		val path = getNotePath(noteContent.id.toInt()).toOkioPath()

		val noteToml = toml.encodeToString(note)

		fileSystem.write(path, mustCreate = false) {
			writeUtf8(noteToml)
		}
	}

	private fun loadNote(path: Path): NoteContainer {
		val noteToml = fileSystem.read(path) {
			readUtf8()
		}

		val note: NoteContainer = toml.decodeFromString(noteToml)
		return note
	}

	companion object {
		fun getNotesDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val projOkPath = projectDef.path.toOkioPath()
			val sceneDirPath = projOkPath.div(NOTES_DIRECTORY)
			if (!fileSystem.exists(sceneDirPath)) {
				fileSystem.createDirectories(sceneDirPath)
			}
			return sceneDirPath.toHPath()
		}
	}
}

fun Sequence<Path>.filterNotePathsOkio() =
	map { it.toHPath() }
		.filterNotesPaths()
		.map { it.toOkioPath() }
		.filter { path -> !path.segments.any { part -> part.startsWith(".") } }