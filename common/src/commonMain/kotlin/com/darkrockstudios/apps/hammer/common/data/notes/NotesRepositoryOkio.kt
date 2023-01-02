package com.darkrockstudios.apps.hammer.common.data.notes

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import okio.FileSystem

class NotesRepositoryOkio(
	projectDef: ProjectDef,
	private val fileSystem: FileSystem
) : NotesRepository(projectDef) {

}