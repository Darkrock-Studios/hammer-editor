package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface Encyclopedia : HammerComponent {
	val state: Value<State>

	fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: List<String>
	): EntryResult

	data class State(
		val projectDef: ProjectDef,
		val entryDefs: List<EntryDef>
	)
}