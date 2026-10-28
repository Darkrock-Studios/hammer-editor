package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryImageFile
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex

interface BrowseEntries {

	val state: Value<State>
	val filterText: Value<String>
	val tagIndex: Value<TagIndex>

	data class State(
		val entryDefs: List<EntryDef> = emptyList(),
		val filterType: EntryType? = null,
	)

	fun updateFilter(text: String?, type: EntryType?)
	fun getFilteredEntries(): List<EntryDef>
	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
	suspend fun loadEntryImage(entry: EntryContent): EntryImageFile?
	fun addTagToSearch(tag: String)
	fun clearFilterText()
}
