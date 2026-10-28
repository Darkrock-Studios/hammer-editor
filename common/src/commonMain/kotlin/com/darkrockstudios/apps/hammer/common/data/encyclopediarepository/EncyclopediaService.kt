package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddOutcome
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharedFlow
import okio.IOException

/**
 * The Component-facing API for the encyclopedia domain. Components and UI talk only to this
 * service; [EncyclopediaRepository] is a lower-level building block. The service owns write
 * orchestration: it applies the cross-cutting side-effects (statistics, reference index, the Image
 * entities an entry shows) that the repository deliberately does not reach sideways to perform,
 * and delegates reads one-to-one. Lower-level data consumers (services, use-cases, sync) still use
 * the repository.
 */
class EncyclopediaService(
	private val repository: EncyclopediaRepository,
	private val statisticsRepository: StatisticsRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
	private val imagesRepository: ImagesRepository,
	private val externalFileIo: ExternalFileIo,
) {

	// region Writes / orchestration

	/**
	 * Creates an entry. With [imagePath], the picked file then becomes a new Image entity named
	 * after the entry and is the entry's primary image; an image that cannot be read or is not a
	 * valid image is skipped and the entry keeps only [images].
	 */
	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		forceId: Int? = null,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean = false,
		images: List<Int> = emptyList(),
	): EntryResult {
		val result = repository.createEntry(
			name = name,
			type = type,
			text = text,
			tags = tags,
			forceId = forceId,
			aliases = aliases,
			excludeFromDictionary = excludeFromDictionary,
			images = images,
		)
		val entry = result.instance?.entry
		if (result.error != EntryError.NONE || entry == null) return result
		statisticsRepository.markDirty()

		val image = imagePath?.let { importImage(entry.name, it).image } ?: return result
		val attached = try {
			repository.addPrimaryImage(repository.getEntryDef(entry.id), image.id)
		} catch (e: IOException) {
			Napier.w("Failed to list image ${image.id} in new entry ${entry.id}", e)
			false
		}
		if (!attached) return result
		return EntryResult(EntryContainer(entry.copy(images = listOf(image.id) + entry.images)), EntryError.NONE)
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean,
		images: List<Int>,
	): EntryResult {
		val result =
			repository.updateEntry(oldEntryDef, name, text, tags, aliases, excludeFromDictionary, images)
		if (result.error == EntryError.NONE) statisticsRepository.markDirty()
		return result
	}

	suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		val deleted = repository.deleteEntry(entryDef)
		if (deleted) {
			statisticsRepository.markDirty()
			referenceIndexRepository.markEntryDeleted(entryDef.id)
		}
		return deleted
	}

	suspend fun addEntryImage(entryDef: EntryDef, imageId: Int): Boolean =
		repository.addEntryImage(entryDef, imageId)

	/** Detaches the image from the entry; the Image entity stays in the project. */
	suspend fun removeEntryImage(entryDef: EntryDef, imageId: Int): Boolean =
		repository.removeEntryImage(entryDef, imageId)

	suspend fun setPrimaryImage(entryDef: EntryDef, imageId: Int): Boolean =
		repository.setPrimaryImage(entryDef, imageId)

	/** Imports the file at [imagePath] as a new Image entity and makes it the entry's primary image. */
	suspend fun importPrimaryImage(entryDef: EntryDef, imagePath: String): ImageAddOutcome {
		val outcome = importImage(entryDef.name, imagePath)
		outcome.image?.let { attachAsPrimary(entryDef, it) }
		return outcome
	}

	/** [importPrimaryImage] for image bytes already in hand. */
	suspend fun importPrimaryImage(entryDef: EntryDef, extension: String, bytes: ByteArray): ImageAddOutcome {
		val outcome = imagesRepository.create(
			name = ImagesDatasource.validImageName(entryDef.name),
			bytes = bytes,
			fileExtension = extension.removePrefix("."),
		)
		outcome.image?.let { attachAsPrimary(entryDef, it) }
		return outcome
	}

	private suspend fun attachAsPrimary(entryDef: EntryDef, image: ImageDef) {
		repository.addPrimaryImage(entryDef, image.id)
	}

	private suspend fun importImage(entryName: String, imagePath: String): ImageAddOutcome {
		val bytes = try {
			externalFileIo.readExternalFile(imagePath)
		} catch (e: IOException) {
			Napier.w("Failed to read entry image: $imagePath", e)
			return ImageAddOutcome(ImageAddResult.ReadFailed)
		}
		val outcome = imagesRepository.create(
			name = ImagesDatasource.validImageName(entryName),
			bytes = bytes,
			fileExtension = imagePath.substringAfterLast('.', ""),
		)
		if (outcome.result != ImageAddResult.Ok) {
			Napier.w("Ignoring entry image $imagePath: ${outcome.result}")
		}
		return outcome
	}

	// endregion

	// region Delegated reads

	val entryListFlow: SharedFlow<List<EntryDef>> get() = repository.entryListFlow

	val entryChangedFlow: SharedFlow<Int> get() = repository.entryChangedFlow

	fun loadEntries() = repository.loadEntries()

	suspend fun ensureEntriesLoaded(): List<EntryDef> = repository.ensureEntriesLoaded()

	fun loadEntry(entryDef: EntryDef): EntryContainer = repository.loadEntry(entryDef)

	fun loadEntry(id: Int): EntryContainer = repository.loadEntry(id)

	fun findEntryDef(id: Int): EntryDef? = repository.findEntryDef(id)

	/** The entry's first image that exists in the project, or null; also null when the entry cannot be read. */
	suspend fun findPrimaryImage(entryDef: EntryDef): ImageDef? {
		val images = try {
			repository.loadEntry(entryDef).entry.images
		} catch (e: EntryLoadError) {
			Napier.w("Failed to read entry ${entryDef.id} for its image", e)
			return null
		}
		return findPrimaryImage(images)
	}

	/** The first of [imageIds] that exists in the project, or null. */
	suspend fun findPrimaryImage(imageIds: List<Int>): ImageDef? {
		if (imageIds.isEmpty()) return null
		val loaded = imagesRepository.ensureImagesLoaded().associateBy { it.id }
		return imageIds.firstNotNullOfOrNull { loaded[it] }
	}

	/** The file of [entry]'s primary image and the hash of its bytes, or null when it has none. */
	suspend fun primaryImageFile(entry: EntryContent): EntryImageFile? {
		val image = findPrimaryImage(entry.images) ?: return null
		return EntryImageFile(imagesRepository.getImagePath(image), imagesRepository.contentHash(image))
	}

	// endregion
}

/**
 * Where an entry's primary image is on disk. [contentHash] changes whenever the bytes do, unlike
 * the path, so it keys image caches; null when the file cannot be read.
 */
data class EntryImageFile(val path: HPath, val contentHash: String?)
