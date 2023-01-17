package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewEntryComponent(
	componentContext: ComponentContext,
	entryDef: EntryDef
) : ProjectComponentBase(entryDef.projectDef, componentContext), ViewEntry {

	private val _state = MutableValue(ViewEntry.State(entryDef = entryDef))
	override val state: Value<ViewEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	init {
		reload()
	}

	private fun reload() {
		scope.launch {
			val entryImagePath = getImagePath(state.value.entryDef)
			val content = loadEntryContent(state.value.entryDef)
			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(
						entryImagePath = entryImagePath,
						content = content
					)
				}
			}
		}
	}

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

	override suspend fun removeEntryImage(): Boolean {
		if (encyclopediaRepository.removeEntryImage(state.value.entryDef)) {
			reload()
		}
		return true
	}

	override suspend fun setImage(path: String) {
		encyclopediaRepository.setEntryImage(state.value.entryDef, path)
		reload()
	}

	override suspend fun updateEntry(
		name: String,
		text: String,
		tags: List<String>
	): EntryResult {
		val result = encyclopediaRepository.updateEntry(state.value.entryDef, name, text, tags)
		if (result.instance != null && result.error == EntryError.NONE) {
			_state.reduce {
				it.copy(
					entryDef = result.instance.toDef(projectDef)
				)
			}

			reload()
		}

		return result
	}
}