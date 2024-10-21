package com.darkrockstudios.apps.hammer.common.data.notesrepository

import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path

class NotesRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	projectSynchronizer: ClientProjectSynchronizer,
	private val fileSystem: FileSystem,
	private val toml: Toml
) : NotesRepository(projectDef, idRepository, projectSynchronizer) {

	init {
		projectScope.scope.registerCallback(this)
		loadNotes()
	}

	override fun getNotesDirectory() = getNotesDirectory(projectDef, fileSystem)

	override fun getNotePath(id: Int): HPath = getNotePath(id, projectDef, fileSystem)

	override fun loadNotes(onLoaded: (() -> Unit)?) {
		notesScope.launch {
			val dir = getNotesDirectory().toOkioPath()
			val files = fileSystem.listRecursively(dir)
			val noteFiles = files.filterNotePathsOkio()

			val notes = noteFiles.map { path -> loadNote(path) }.toList()
			updateNotes(notes)

			onLoaded?.invoke()
		}
	}

	override suspend fun createNote(noteText: String): CResult<NoteContent> {
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

			val path = getNotePath(newId).toOkioPath()
			val noteToml = toml.encodeToString(newNote)
			fileSystem.write(path, mustCreate = true) {
				writeUtf8(noteToml)
			}

			markForSync(
				id = newId,
				""
			)

			CResult.success(newNote.note)
		}
	}

	override suspend fun deleteNote(id: Int) {
		val path = getNotePath(id).toOkioPath()
		fileSystem.delete(path, true)
		projectSynchronizer.recordIdDeletion(id)
	}

	override suspend fun updateNote(noteContent: NoteContent, markForSync: Boolean) {
		val noteContainer = NoteContainer(noteContent)
		val path = getNotePath(noteContent.id).toOkioPath()

		val noteToml = toml.encodeToString(noteContainer)

		fileSystem.write(path, mustCreate = false) {
			writeUtf8(noteToml)
		}

		if (markForSync) {
			markForSync(id = noteContent.id)
		}
	}

	private fun loadNote(path: Path): NoteContainer {
		val noteToml = fileSystem.read(path) {
			readUtf8()
		}

		val note: NoteContainer = toml.decodeFromString(noteToml)
		return note
	}

	override suspend fun reIdNote(oldId: Int, newId: Int) {
		val oldPath = getNotePath(oldId).toOkioPath()
		val newPath = getNotePath(newId).toOkioPath()
		fileSystem.atomicMove(oldPath, newPath)

		// Rewrite the file because the ID is contained in there
		val noteContainer = loadNote(newPath)
		val newNote = noteContainer.note.copy(id = newId)
		fileSystem.write(newPath, mustCreate = false) {
			writeUtf8(toml.encodeToString(NoteContainer(newNote)))
		}

		// Update the in-memory notes list
		val updatedNotes = getNotes().toMutableList()
		updatedNotes.indexOf(noteContainer).let { index ->
			updatedNotes[index] = noteContainer.copy(note = newNote)
		}
		updateNotes(updatedNotes)
	}

	override suspend fun getNoteById(id: Int): NoteContainer? {
		return notesListFlow.first().find { it.note.id == id }
	}

	companion object {

		fun getNotePath(id: Int, projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val dir = getNotesDirectory(projectDef, fileSystem).toOkioPath()
			val path = dir / getNoteFilenameFromId(id)
			return path.toHPath()
		}

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