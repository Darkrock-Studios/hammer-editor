package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
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
	 * Creates an entry. With [imagePath], the picked file first becomes a new Image entity named
	 * after the entry and is the entry's first image; an image that cannot be read or is not a
	 * valid image is skipped and the entry is created without it.
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
		var allImages = images
		if (imagePath != null) {
			val error = repository.validateEntry(name, type, text, tags, aliases)
			if (error != EntryError.NONE) return EntryResult(error)

			val imported = importImage(name, imagePath)
			imported.image?.let { allImages = allImages + it.id }
		}

		val result = repository.createEntry(
			name = name,
			type = type,
			text = text,
			tags = tags,
			forceId = forceId,
			aliases = aliases,
			excludeFromDictionary = excludeFromDictionary,
			images = allImages,
		)
		if (result.error == EntryError.NONE) statisticsRepository.markDirty()
		return result
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
		repository.addEntryImage(entryDef, image.id)
		repository.setPrimaryImage(entryDef, image.id)
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
	fun findPrimaryImage(entryDef: EntryDef): ImageDef? {
		val images = try {
			repository.loadEntry(entryDef).entry.images
		} catch (e: EntryLoadError) {
			Napier.w("Failed to read entry ${entryDef.id} for its image", e)
			return null
		}
		return images.firstNotNullOfOrNull { imagesRepository.findImage(it) }
	}

	fun getImagePath(image: ImageDef): HPath = imagesRepository.getImagePath(image)

	fun imageContentHash(image: ImageDef): String? = imagesRepository.contentHash(image)

	// endregion
}
