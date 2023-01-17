package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.projectInject

class ViewEntryComponent(
	componentContext: ComponentContext,
	entryDef: EntryDef
) : ProjectComponentBase(entryDef.projectDef, componentContext), ViewEntry {

	private val _state = MutableValue(ViewEntry.State(entryDef = entryDef))
	override val state: Value<ViewEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	override fun getImagePath(entryDef: EntryDef): String? {
		return if (encyclopediaRepository.hasEntryImage(entryDef, "jpg")) {
			encyclopediaRepository.getEntryImagePath(entryDef, "jpg").path
		} else {
			null
		}
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val container = encyclopediaRepository.loadEntry(entryDef)
		return container.entry
	}

	override suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		encyclopediaRepository.deleteEntry(entryDef)
		return true
	}
}