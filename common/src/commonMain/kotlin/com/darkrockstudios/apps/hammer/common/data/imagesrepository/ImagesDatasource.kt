package com.darkrockstudios.apps.hammer.common.data.imagesrepository

import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.IOException
import okio.Path

/** Size and modification time of an image file, used to tell whether a cached hash is still current. */
data class ImageFileStamp(val size: Long, val lastModifiedAtMillis: Long)

class ImagesDatasource(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
) {
	fun getImagesDirectory(): HPath = getImagesDirectory(projectDef, fileSystem)

	fun getImagePath(def: ImageDef): HPath =
		(getImagesDirectory().toOkioPath() / getFilename(def)).toHPath()

	/** Every image on disk, ordered by id. Files that do not parse as an image are skipped. */
	fun loadImages(): List<ImageDef> {
		val dir = getImagesDirectory().toOkioPath()
		return listImageFiles(dir, fileSystem)
			.map { (_, def) -> def }
			.sortedBy { it.id }
			.toList()
	}

	fun findImage(id: Int): ImageDef? = findImagePath(id)?.let { parseFilename(it.name) }

	fun findImagePath(id: Int): HPath? {
		val dir = getImagesDirectory().toOkioPath()
		return listImageFiles(dir, fileSystem)
			.firstOrNull { (_, def) -> def.id == id }
			?.first
			?.toHPath()
	}

	fun loadImageBytes(def: ImageDef): ByteArray? {
		val path = getImagePath(def).toOkioPath()
		return try {
			fileSystem.read(path) { readByteArray() }
		} catch (e: IOException) {
			Napier.e("Failed to read image ${def.id}: $path", e)
			null
		}
	}

	fun getFileStamp(def: ImageDef): ImageFileStamp? {
		val metadata = fileSystem.metadataOrNull(getImagePath(def).toOkioPath()) ?: return null
		val size = metadata.size ?: return null
		val lastModified = metadata.lastModifiedAtMillis ?: return null
		return ImageFileStamp(size, lastModified)
	}

	/**
	 * Writes [bytes] as the image [def], replacing any other file that holds the same id (the
	 * extension or name may differ).
	 *
	 * The bytes go to a hidden temp file first, which then atomically replaces the target, so a failed
	 * write never loses the old image. An old file whose name differs from the target only in case is
	 * deleted before the move instead of after: on a case-insensitive filesystem it is the target.
	 */
	fun writeImage(def: ImageDef, bytes: ByteArray) {
		val dir = getImagesDirectory().toOkioPath()
		val targetPath = dir / getFilename(def)
		if (!targetPath.isWithin(dir) || targetPath.parent != dir) {
			error("Refusing to write image outside the images directory: $targetPath")
		}

		val tempPath = dir / ".${def.id}~write.tmp"
		try {
			fileSystem.write(tempPath) {
				write(bytes)
			}

			val (caseVariants, others) = listImageFiles(dir, fileSystem)
				.filter { (path, existingDef) -> existingDef.id == def.id && path != targetPath }
				.map { it.first }
				.partition { it.name.equals(targetPath.name, ignoreCase = true) }

			caseVariants.forEach { fileSystem.delete(it) }
			fileSystem.atomicMove(tempPath, targetPath)
			others.forEach { fileSystem.delete(it) }
		} finally {
			deleteTempFile(tempPath)
		}
	}

	// Must not throw: it runs in a finally block and would hide the write's own failure.
	private fun deleteTempFile(tempPath: Path) {
		try {
			fileSystem.delete(tempPath, mustExist = false)
		} catch (e: IOException) {
			Napier.w("Failed to delete temp image file $tempPath", e)
		}
	}

	fun renameImage(def: ImageDef, newName: String): ImageDef {
		val renamed = def.copy(name = newName)
		val oldPath = getImagePath(def).toOkioPath()
		val newPath = getImagePath(renamed).toOkioPath()
		if (oldPath != newPath) {
			fileSystem.atomicMove(oldPath, newPath)
		}
		return renamed
	}

	fun deleteImage(id: Int): Boolean {
		val dir = getImagesDirectory().toOkioPath()
		val paths = listImageFiles(dir, fileSystem)
			.filter { (_, def) -> def.id == id }
			.map { it.first }
			.toList()
		paths.forEach { fileSystem.delete(it) }
		return paths.isNotEmpty()
	}

	fun reIdImage(oldId: Int, newId: Int): ImageDef? {
		val oldPath = findImagePath(oldId)?.toOkioPath() ?: return null
		val oldDef = parseFilename(oldPath.name) ?: return null
		val newDef = oldDef.copy(id = newId)
		fileSystem.atomicMove(oldPath, getImagePath(newDef).toOkioPath())
		return newDef
	}

	companion object {
		const val IMAGES_DIRECTORY = "images"

		/** Used when nothing of a candidate name survives as a valid image name. */
		const val FALLBACK_IMAGE_NAME = "Image"

		// <id>~<encoded name>.<extension>. The name may contain dots; the extension is after the last one.
		val IMAGE_FILENAME_PATTERN = Regex("""(\d+)~([^~/\\]+)\.([A-Za-z0-9]+)""")

		fun getFilename(def: ImageDef): String =
			"${def.id}~${ProjectsRepository.encodeForFilename(def.name)}.${def.fileExtension}"

		/**
		 * [candidate] as an image name: trimmed, or run through [ProjectsRepository.sanitizeFileName]
		 * with any trailing `.` or space dropped; or [FALLBACK_IMAGE_NAME] when that leaves nothing
		 * valid.
		 */
		fun validImageName(candidate: String): String {
			val trimmed = candidate.trim()
			if (ImageFormat.isValidName(trimmed)) return trimmed
			// Truncating, or trimming a dot off "Chapter 1 .", can leave a trailing space or dot.
			val sanitized = ProjectsRepository.sanitizeFileName(trimmed).trimEnd('.', ' ')
			return sanitized.takeIf { ImageFormat.isValidName(it) } ?: FALLBACK_IMAGE_NAME
		}

		fun parseFilename(fileName: String): ImageDef? {
			if (fileName.startsWith(".")) return null
			val match = IMAGE_FILENAME_PATTERN.matchEntire(fileName) ?: return null
			val id = match.groupValues[1].toIntOrNull() ?: return null
			val extension = match.groupValues[3]
			if (!ImageFormat.isAllowedExtension(extension)) return null
			return ImageDef(
				id = id,
				name = ProjectsRepository.decodeFromFilename(match.groupValues[2]),
				fileExtension = extension,
			)
		}

		fun getImagesDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val dir = projectDef.path.toOkioPath() / IMAGES_DIRECTORY
			if (!fileSystem.exists(dir)) {
				fileSystem.createDirectories(dir)
			}
			return dir.toHPath()
		}

		internal fun listImageFiles(dir: Path, fileSystem: FileSystem): Sequence<Pair<Path, ImageDef>> =
			fileSystem.listOrNull(dir).orEmpty()
				.asSequence()
				.mapNotNull { path -> parseFilename(path.name)?.let { path to it } }
	}
}

/**
 * Locates an image file without a project scope (the project list shows covers for projects
 * that are not open). Does not create the images directory.
 */
fun findImageFile(projectDef: ProjectDef, id: Int, fileSystem: FileSystem): HPath? {
	val dir = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY
	if (!fileSystem.exists(dir)) return null
	return ImagesDatasource.listImageFiles(dir, fileSystem)
		.firstOrNull { (_, def) -> def.id == id }
		?.first
		?.toHPath()
}
