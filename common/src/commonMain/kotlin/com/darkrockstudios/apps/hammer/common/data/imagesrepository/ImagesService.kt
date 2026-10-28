package com.darkrockstudios.apps.hammer.common.data.imagesrepository

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import okio.IOException

/**
 * [touched] are the entities a reference was taken out of; [coverCleared] is true when the image was
 * the project's cover. [deleted] is false when there was no such image.
 */
data class ImageDeleteResult(
	val deleted: Boolean,
	val touched: Set<TouchedEntity>,
	val coverCleared: Boolean = false,
)

/**
 * The Component-facing API for the project's Image entities. Adds the work the repository does not
 * do itself: reading picked files from outside the project, and taking every reference out of
 * other entities before an image is deleted.
 */
class ImagesService(
	private val imagesRepository: ImagesRepository,
	private val referenceRewriter: ImageReferenceRewriter,
	private val externalFileIo: ExternalFileIo,
) {
	val imagesFlow: SharedFlow<List<ImageDef>> get() = imagesRepository.imagesFlow

	/** Emits the id of an image whose bytes, name or id changed, or that was deleted. */
	val imageChangedFlow: SharedFlow<Int> get() = imagesRepository.imageChangedFlow

	suspend fun ensureImagesLoaded(): List<ImageDef> = imagesRepository.ensureImagesLoaded()

	fun findImage(id: Int): ImageDef? = imagesRepository.findImage(id)

	/** Reads the whole image to hash it unless its hash is cached; [imagePath] and [cachedContentHash] do not. */
	fun imageFile(def: ImageDef): ImageFile = imagesRepository.imageFile(def)

	fun imagePath(def: ImageDef): HPath = imagesRepository.getImagePath(def)

	/** The file of image [id], or null when the project has no such image. Reads it to hash it unless cached. */
	suspend fun loadImageFile(id: Int): ImageFile? {
		imagesRepository.ensureImagesLoaded()
		return imagesRepository.findImage(id)?.let(imagesRepository::imageFile)
	}

	/** The size and modification time of [def]'s file, or null when it is missing. */
	fun fileStamp(def: ImageDef): ImageFileStamp? = imagesRepository.getFileStamp(def)

	/** [ImageFile.contentHash] when it is already known for the file's [stamp]; never reads the file. */
	fun cachedContentHash(def: ImageDef, stamp: ImageFileStamp): String? =
		imagesRepository.cachedContentHash(def, stamp)

	fun loadImageBytes(def: ImageDef): ByteArray? = imagesRepository.loadImageBytes(def)

	fun isValidName(name: String): Boolean = imagesRepository.isValidName(name)

	/** Adds the file at [path] as a new image named after the file. */
	suspend fun addFromPath(path: String): ImageAddOutcome {
		val bytes = readExternal(path) ?: return ImageAddOutcome(ImageAddResult.ReadFailed)
		val fileName = path.substringAfterLast('/').substringAfterLast('\\')
		return add(
			name = ImagesDatasource.validImageName(fileName.substringBeforeLast('.')),
			fileExtension = extensionOf(fileName),
			bytes = bytes,
		)
	}

	suspend fun add(name: String, fileExtension: String, bytes: ByteArray): ImageAddOutcome =
		imagesRepository.create(name = name, bytes = bytes, fileExtension = fileExtension.removePrefix("."))

	/** Replaces the bytes of image [id] with the file at [path], keeping its id and name. */
	suspend fun replaceFromPath(id: Int, path: String): ImageAddOutcome {
		val bytes = readExternal(path) ?: return ImageAddOutcome(ImageAddResult.ReadFailed)
		return replace(id, extensionOf(path), bytes)
	}

	suspend fun replace(id: Int, fileExtension: String, bytes: ByteArray): ImageAddOutcome =
		imagesRepository.replace(id, bytes, fileExtension.removePrefix("."))

	suspend fun rename(id: Int, name: String): ImageAddOutcome = imagesRepository.rename(id, name)

	suspend fun findUsages(id: Int): ImageUsages = referenceRewriter.findUsages(id)

	/** Whether anything in the project refers to image [id]; stops at the first use. */
	suspend fun isUsed(id: Int): Boolean = referenceRewriter.isUsed(id)

	/** The ids of the images something in the project refers to. */
	suspend fun findUsedImageIds(): Set<Int> = referenceRewriter.findReferencedIds()

	/**
	 * Removes every reference to image [id] from the rest of the project, then deletes it. The
	 * rewriter marks each entity it changes dirty for sync.
	 */
	suspend fun delete(id: Int): ImageDeleteResult {
		if (imagesRepository.findImage(id) == null) return ImageDeleteResult(deleted = false, touched = emptySet())
		val removal = referenceRewriter.removeAll(id)
		val deleted = imagesRepository.delete(id)
		return ImageDeleteResult(deleted = deleted, touched = removal.touched, coverCleared = removal.coverCleared)
	}

	private fun readExternal(path: String): ByteArray? = try {
		externalFileIo.readExternalFile(path)
	} catch (e: IOException) {
		Napier.w("Failed to read image file: $path", e)
		null
	}

	private fun extensionOf(path: String): String = path.substringAfterLast('.', "")
}
