package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository

/** Edits the project's user dictionary words; every writer goes through [normalizeDictionaryWord]. */
class ProjectDictionaryUseCase(
	private val projectDataRepository: ProjectDataRepository,
) {
	/** Input that normalizes to nothing storable, or a word already present in any case, is ignored. */
	suspend fun addWord(raw: String) {
		val word = normalizeDictionaryWord(raw) ?: return
		projectDataRepository.updateData { data ->
			if (data.dictionaryWords.any { it.equals(word, ignoreCase = true) }) data
			else data.copy(dictionaryWords = data.dictionaryWords + word)
		}
	}

	suspend fun removeWord(word: String) {
		projectDataRepository.updateData { it.copy(dictionaryWords = it.dictionaryWords - word) }
	}
}
