package com.darkrockstudios.apps.hammer.common.data.id.datasources

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem

class ImageIdDatasource(
	private val fileSystem: FileSystem
) : IdDatasource {
	override fun findIds(projectDef: ProjectDef): Set<Int>? {
		val dir = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY
		if (!fileSystem.exists(dir)) return emptySet()
		val files = fileSystem.listOrNull(dir) ?: return null
		return files.mapNotNull { path -> ImagesDatasource.parseFilename(path.name)?.id }.toSet()
	}
}
