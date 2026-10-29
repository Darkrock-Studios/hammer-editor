package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddOutcome
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
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
	private val imagesService: ImagesService,
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

	/**
	 * Imports the file at [imagePath] as a new Image entity named after the entry and adds it to the
	 * end of the entry's images, so it is the primary image only when the entry had none.
	 */
	suspend fun importEntryImage(entryDef: EntryDef, imagePath: String): ImageAddOutcome {
		val outcome = importImage(entryDef.name, imagePath)
		outcome.image?.let { repository.addEntryImage(entryDef, it.id) }
		return outcome
	}

	/** [importEntryImage] for image bytes already in hand. */
	suspend fun importEntryImage(entryDef: EntryDef, extension: String, bytes: ByteArray): ImageAddOutcome {
		val outcome = imagesService.add(ImagesDatasource.validImageName(entryDef.name), extension, bytes)
		outcome.image?.let { repository.addEntryImage(entryDef, it.id) }
		return outcome
	}

	private suspend fun importImage(entryName: String, imagePath: String): ImageAddOutcome {
		val outcome = imagesService.addFromPath(imagePath, name = entryName)
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

	/** The first of [imageIds] that exists in the project, or null. */
	suspend fun findPrimaryImage(imageIds: List<Int>): ImageDef? {
		if (imageIds.isEmpty()) return null
		val loaded = imagesRepository.ensureImagesLoaded().associateBy { it.id }
		return imageIds.firstNotNullOfOrNull { loaded[it] }
	}

	/** The file of [entry]'s primary image, or null when it has none. */
	suspend fun primaryImageFile(entry: EntryContent): ImageFile? =
		findPrimaryImage(entry.images)?.let(imagesRepository::imageFile)

	/** The files of [entry]'s images that exist in the project, primary first. */
	suspend fun entryImageFiles(entry: EntryContent): List<ImageFile> =
		imagesRepository.findImageFiles(entry.images)

	/** Emits the id of an image whose bytes, name or id changed, or that was deleted. */
	val imageChangedFlow: SharedFlow<Int> get() = imagesRepository.imageChangedFlow

	// endregion
}
