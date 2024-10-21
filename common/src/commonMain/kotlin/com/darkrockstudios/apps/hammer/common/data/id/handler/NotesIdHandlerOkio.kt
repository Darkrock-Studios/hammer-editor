package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.filterNotePathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class NotesIdHandlerOkio(
	private val fileSystem: FileSystem
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val notesDir = NotesDatasource.getNotesDirectory(projectDef, fileSystem).toOkioPath()

		val maxId: Int = fileSystem.listRecursively(notesDir)
			.filterNotePathsOkio().maxOfOrNull { path ->
				NotesDatasource.getNoteIdFromFilename(path.name)
			} ?: -1

		return maxId
	}
}