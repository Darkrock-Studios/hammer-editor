package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

interface BrowseEntries {

	val state: Value<State>

	data class State(
		val entryDefs: List<EntryDef> = emptyList(),
		val filterText: String? = null,
		val filterType: EntryType? = null,
	)

	fun updateFilter(text: String?, type: EntryType?)
	fun getFilteredEntries(): List<EntryDef>
	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
	fun getImagePath(entryDef: EntryDef): String?
}