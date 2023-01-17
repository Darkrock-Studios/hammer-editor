package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

interface CreateEntry {

	val state: Value<State>

	data class State(
		val projectDef: ProjectDef
	)

	fun createEntry(name: String, type: EntryType, text: String, tags: List<String>, imagePath: String?): EntryResult
}