package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

interface ViewEntry {

	val state: Value<State>

	data class State(
		val entryDef: EntryDef
	)

	fun getImagePath(entryDef: EntryDef): String?
	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
}