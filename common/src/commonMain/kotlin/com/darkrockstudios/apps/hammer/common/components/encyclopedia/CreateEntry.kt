package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting

interface CreateEntry : TagSuggesting {

	val state: Value<State>

	data class State(
		val projectDef: ProjectDef,
		val showConfirmClose: Boolean = false,
		val spellCheckAllowed: Boolean = true,
		val dictionaryFeatureEnabled: Boolean = true,
	)

	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		excludeFromDictionary: Boolean = false,
	): EntryResult

	fun confirmClose()
	fun dismissConfirmClose()
}