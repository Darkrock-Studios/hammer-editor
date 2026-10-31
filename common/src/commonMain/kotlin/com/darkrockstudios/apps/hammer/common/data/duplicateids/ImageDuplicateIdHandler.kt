package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.FileSystem

/** Image ids are in the filename. Copies are ordered like the image lookup by id: listing order. */
internal class ImageDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Image

	override val entityType = EntityType.Image

	private val imagesDir get() = projectDir / ImagesDatasource.IMAGES_DIRECTORY

	private fun files() = ImagesDatasource.listImageFiles(imagesDir, fileSystem).toList()

	override fun ids(): Set<Int> = files().mapTo(mutableSetOf()) { (_, def) -> def.id }

	override fun findDuplicates(): List<DuplicateIdSet> =
		files().toDuplicateSets({ (_, def) -> def.id }) { (path, def) ->
			copyOf(path, def.name, readBytes(path), text = "")
		}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath {
		val def = ImagesDatasource.parseFilename(copy.path.name) ?: error("Not an image: ${copy.path}")
		return (imagesDir / ImagesDatasource.getFilename(def.copy(id = id))).toHPath()
	}
}
