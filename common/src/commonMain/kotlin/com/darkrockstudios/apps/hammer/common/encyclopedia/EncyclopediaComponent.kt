package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EncyclopediaComponent(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {

	private val _state = MutableValue(Encyclopedia.State(projectDef = projectDef, entryDefs = emptyList()))
	override val state: Value<Encyclopedia.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	init {
		scope.launch {
			encyclopediaRepository.entryListFlow.collect { entryDefs ->
				withContext(mainDispatcher) {
					_state.reduce { state ->
						state.copy(
							entryDefs = entryDefs
						)
					}
				}
			}
		}

		encyclopediaRepository.loadEntries()
	}

	override fun createEntry(name: String, type: EntryType, text: String, tags: List<String>): EntryResult {
		val result = encyclopediaRepository.createEntry(name, type, text, tags)
		if (result.error == EntryError.NONE) {
			encyclopediaRepository.loadEntries()
		}

		return result
	}
}