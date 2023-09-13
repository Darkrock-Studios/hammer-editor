package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.handler.*
import kotlinx.serialization.json.Json
import okio.FileSystem

class IdRepositoryOkio(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	//toml: Toml
	json: Json
) : IdRepository(projectDef) {
	override val idHandlers: List<IdHandler> = listOf(
		SceneIdHandlerOkio(fileSystem),
		NotesIdHandlerOkio(fileSystem),
		EncyclopediaIdHandlerOkio(fileSystem),
		TimeLineEventIdHandlerOkio(fileSystem, json),
	)
}