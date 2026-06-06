package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository

/**
 * The Component-facing API for encyclopedia writes. Wraps [EncyclopediaRepository]
 * and applies the cross-cutting side-effects (statistics, reference index) that the
 * repository deliberately does not reach sideways to perform. Reads still go through
 * the repository directly.
 */
class EncyclopediaService(
	private val repository: EncyclopediaRepository,
	private val statisticsRepository: StatisticsRepository,
	private val referenceIndexRepository: ReferenceIndexRepository,
) {

	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		forceId: Int? = null,
		aliases: List<String> = emptyList(),
	): EntryResult {
		val result = repository.createEntry(name, type, text, tags, imagePath, forceId, aliases)
		if (result.error == EntryError.NONE) statisticsRepository.markDirty()
		return result
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
		aliases: List<String> = emptyList(),
	): EntryResult {
		val result = repository.updateEntry(oldEntryDef, name, text, tags, aliases)
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
}
