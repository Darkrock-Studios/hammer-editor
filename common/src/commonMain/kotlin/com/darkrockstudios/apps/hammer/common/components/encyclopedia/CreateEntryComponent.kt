package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject

class CreateEntryComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), CreateEntry {

	private val _state = MutableValue(CreateEntry.State(projectDef = projectDef))
	override val state: Value<CreateEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val backButtonHandler = BackCallback {
		confirmClose()
	}

	init {
		backHandler.register(backButtonHandler)
	}

	override fun confirmClose() {
		_state.getAndUpdate {
			it.copy(showConfirmClose = true)
		}
	}

	override fun dismissConfirmClose() {
		_state.getAndUpdate {
			it.copy(showConfirmClose = false)
		}
	}

	override suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: List<String>,
		imagePath: String?
	): EntryResult {
		val result = encyclopediaRepository.createEntry(name, type, text, tags, imagePath)
		if (result.error == EntryError.NONE) {
			encyclopediaRepository.loadEntries()
		}

		return result
	}
}