package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.filterNotePathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class NotesIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findIds(projectDef: ProjectDef): Set<Int> {
		val notesDir = NotesDatasource.getNotesDirectory(projectDef, fileSystem).toOkioPath()

		return fileSystem.listRecursively(notesDir)
			.filterNotePathsOkio()
			.map { path -> NotesDatasource.getNoteIdFromFilename(path.name) }
			.toSet()
	}
}