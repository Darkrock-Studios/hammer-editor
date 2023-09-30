package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.handler.*
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

class IdRepositoryOkio(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
) : IdRepository(projectDef) {
	override val idHandlers: List<IdHandler> = listOf(
		SceneIdHandlerOkio(fileSystem),
		NotesIdHandlerOkio(fileSystem),
		EncyclopediaIdHandlerOkio(fileSystem),
		TimeLineEventIdHandlerOkio(fileSystem, toml),
	)
}