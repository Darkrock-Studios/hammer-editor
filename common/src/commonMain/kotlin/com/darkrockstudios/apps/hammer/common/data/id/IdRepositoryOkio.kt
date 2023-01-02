package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProvider
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProviderOkio
import okio.FileSystem

class IdRepositoryOkio(private val fileSystem: FileSystem) : IdRepository() {
	override fun createNewProvider(projectDef: ProjectDef): IdProvider {
		return IdProviderOkio(projectDef, fileSystem)
	}
}