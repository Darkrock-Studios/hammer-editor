package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

interface ViewEntry {

	val state: Value<State>

	data class State(
		val entryDef: EntryDef,
		val entryImagePath: String? = null,
		val content: EntryContent? = null
	)

	fun getImagePath(entryDef: EntryDef): String?
	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
	suspend fun deleteEntry(entryDef: EntryDef): Boolean
	suspend fun updateEntry(name: String, text: String, tags: List<String>): EntryResult
	suspend fun removeEntryImage(): Boolean
	suspend fun setImage(path: String)
}