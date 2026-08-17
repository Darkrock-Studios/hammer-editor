package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.flow.SharedFlow

/**
 * The Component-facing API for the encyclopedia domain. Components and UI talk only to this
 * service; [EncyclopediaRepository] is a lower-level building block. The service owns write
 * orchestration — applying the cross-cutting side-effects (statistics, reference index) that
 * the repository deliberately does not reach sideways to perform — and delegates reads
 * one-to-one. Lower-level data consumers (services, use-cases, sync) still use the repository.
 */
class EncyclopediaService(
	private val repository: EncyclopediaRepository,
	private val statisticsRepository: StatisticsRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
) {

	// region Writes / orchestration

	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		forceId: Int? = null,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean = false,
	): EntryResult {
		val result =
			repository.createEntry(name, type, text, tags, imagePath, forceId, aliases, excludeFromDictionary)
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
	): EntryResult {
		val result = repository.updateEntry(oldEntryDef, name, text, tags, aliases, excludeFromDictionary)
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

	suspend fun setEntryImage(entryDef: EntryDef, imagePath: String?) =
		repository.setEntryImage(entryDef, imagePath)

	suspend fun removeEntryImage(entryDef: EntryDef): Boolean =
		repository.removeEntryImage(entryDef)

	// endregion

	// region Delegated reads

	val entryListFlow: SharedFlow<List<EntryDef>> get() = repository.entryListFlow

	fun loadEntries() = repository.loadEntries()

	suspend fun ensureEntriesLoaded(): List<EntryDef> = repository.ensureEntriesLoaded()

	fun loadEntry(entryDef: EntryDef): EntryContainer = repository.loadEntry(entryDef)

	fun loadEntry(id: Int): EntryContainer = repository.loadEntry(id)

	fun findEntryDef(id: Int): EntryDef? = repository.findEntryDef(id)

	fun hasEntryImage(entryDef: EntryDef, fileExtension: String): Boolean =
		repository.hasEntryImage(entryDef, fileExtension)

	fun findEntryImagePath(entryDef: EntryDef): HPath? =
		repository.findEntryImagePath(entryDef)

	fun findEntryImageExtension(entryDef: EntryDef): String? =
		repository.findEntryImageExtension(entryDef)

	suspend fun calculateEntryImageHash(entryDef: EntryDef, fileExtension: String): String? =
		repository.calculateEntryImageHash(entryDef, fileExtension)

	fun getEntryImagePath(entryDef: EntryDef, fileExtension: String): HPath =
		repository.getEntryImagePath(entryDef, fileExtension)

	fun loadEntryImage(entryDef: EntryDef, fileExtension: String): ByteArray =
		repository.loadEntryImage(entryDef, fileExtension)

	// endregion
}
