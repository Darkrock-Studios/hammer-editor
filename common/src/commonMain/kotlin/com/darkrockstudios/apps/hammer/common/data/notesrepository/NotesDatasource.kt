package com.darkrockstudios.apps.hammer.common.data.notesrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import org.koin.core.component.KoinComponent

class NotesDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
	private val toml: Toml
) : KoinComponent {
	private val ioDispatcher = injectIoDispatcherNow()

	private fun getNotesDirectory() = getNotesDirectory(projectDef, fileSystem)
	fun getNotePath(id: Int): HPath = getNotePath(id, projectDef, fileSystem)

	suspend fun loadNotes(): List<NoteContainer> = withContext(ioDispatcher) {
		val dir = getNotesDirectory().toOkioPath()
		val files = fileSystem.listRecursively(dir)
		val noteFiles = files.filterNotePathsOkio()

		val notes = noteFiles.map { path -> loadNote(path) }.toList()
		return@withContext notes
	}

	private fun loadNote(path: Path): NoteContainer {
		val noteToml = fileSystem.read(path) {
			readUtf8()
		}

		val note: NoteContainer = toml.decodeFromString(noteToml)
		return note
	}

	suspend fun storeNote(newNote: NoteContainer) = withContext(ioDispatcher) {
		val path = getNotePath(newNote.note.id).toOkioPath()
		val noteToml = toml.encodeToString(newNote)
		fileSystem.write(path, mustCreate = true) {
			writeUtf8(noteToml)
		}
	}

	suspend fun deleteNote(id: Int) = withContext(ioDispatcher) {
		val path = getNotePath(id).toOkioPath()
		fileSystem.delete(path, true)
	}

	suspend fun updateNote(noteContent: NoteContent) = withContext(ioDispatcher) {
		val noteContainer = NoteContainer(noteContent)
		val path = getNotePath(noteContent.id).toOkioPath()

		val noteToml = toml.encodeToString(noteContainer)

		fileSystem.write(path, mustCreate = false) {
			writeUtf8(noteToml)
		}
	}

	suspend fun reIdNote(oldId: Int, newId: Int): NoteContent = withContext(ioDispatcher) {
		val oldPath = getNotePath(oldId).toOkioPath()
		val newPath = getNotePath(newId).toOkioPath()
		fileSystem.atomicMove(oldPath, newPath)

		// Rewrite the file because the ID is contained in there
		val noteContainer = loadNote(newPath)
		val newNote = noteContainer.note.copy(id = newId)
		fileSystem.write(newPath, mustCreate = false) {
			writeUtf8(toml.encodeToString(NoteContainer(newNote)))
		}

		return@withContext newNote
	}

	companion object {
		val NOTE_FILENAME_PATTERN = Regex("""note-(\d+)\.toml""")
		const val NOTES_FILENAME_EXTENSION = ".toml"
		const val NOTES_DIRECTORY = "notes"

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

fun Sequence<HPath>.filterNotesPaths() = filter {
	!it.name.startsWith(".") && NotesDatasource.NOTE_FILENAME_PATTERN.matches(it.name)
}.sortedBy { it.name }

fun Sequence<Path>.filterNotePathsOkio() =
	map { it.toHPath() }
		.filterNotesPaths()
		.map { it.toOkioPath() }
		.filter { path -> !path.segments.any { part -> part.startsWith(".") } }