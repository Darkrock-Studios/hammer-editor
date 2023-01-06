package com.darkrockstudios.apps.hammer.common.data.id.handler

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.filterEntryPathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class EncyclopediaIdHandlerOkio(
	private val fileSystem: FileSystem
) : IdHandler {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val dir = EncyclopediaRepositoryOkio.getEncyclopediaDirectory(projectDef, fileSystem).toOkioPath()

		val maxId: Int = fileSystem.listRecursively(dir)
			.filterEntryPathsOkio().maxOfOrNull { path ->
				EncyclopediaRepository.getEntryIdFromFilename(path.name)
			} ?: -1

		return maxId
	}
}