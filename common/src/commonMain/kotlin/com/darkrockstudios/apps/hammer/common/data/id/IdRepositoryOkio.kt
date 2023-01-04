package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.handler.IdHandler
import com.darkrockstudios.apps.hammer.common.data.id.handler.NotesIdHandlerOkio
import com.darkrockstudios.apps.hammer.common.data.id.handler.SceneIdHandlerOkio
import okio.FileSystem

class IdRepositoryOkio(projectDef: ProjectDef, fileSystem: FileSystem) : IdRepository(projectDef) {
	override val idHandlers: List<IdHandler> = listOf(
		SceneIdHandlerOkio(fileSystem),
		NotesIdHandlerOkio(fileSystem)
	)
}