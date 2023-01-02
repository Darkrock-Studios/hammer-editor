package com.darkrockstudios.apps.hammer.common.data.id.provider

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.provider.handler.SceneIdHandlerOkio
import okio.FileSystem

class IdProviderOkio(projectDef: ProjectDef, fileSystem: FileSystem) : IdProvider(projectDef) {
	init {
		idHandlers.add(SceneIdHandlerOkio(fileSystem))
	}
}