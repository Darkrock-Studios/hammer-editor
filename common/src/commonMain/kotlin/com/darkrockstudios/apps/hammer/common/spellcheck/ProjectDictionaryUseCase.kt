package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository

/** Edits the project's user dictionary words; every writer goes through [normalizeDictionaryWord]. */
class ProjectDictionaryUseCase(
	private val projectDataRepository: ProjectDataRepository,
) {
	/** Returns false when [raw] normalizes to nothing storable. */
	suspend fun addWord(raw: String): Boolean {
		val word = normalizeDictionaryWord(raw) ?: return false
		projectDataRepository.updateData { it.copy(dictionaryWords = it.dictionaryWords + word) }
		return true
	}

	suspend fun removeWord(word: String) {
		projectDataRepository.updateData { it.copy(dictionaryWords = it.dictionaryWords - word) }
	}
}
