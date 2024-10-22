package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.filterEntryPathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class EncyclopediaIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findHighestId(projectDef: ProjectDef): Int {
		val dir =
			EncyclopediaDatasource.getEncyclopediaDirectory(projectDef, fileSystem).toOkioPath()

		val maxId: Int = fileSystem.listRecursively(dir)
			.filterEntryPathsOkio().maxOfOrNull { path ->
				EncyclopediaDatasource.getEntryIdFromFilename(path.name)
			} ?: -1

		return maxId
	}
}