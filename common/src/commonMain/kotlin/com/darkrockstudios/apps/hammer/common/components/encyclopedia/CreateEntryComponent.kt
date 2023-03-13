package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectInject
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

class CreateEntryComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), CreateEntry {

	private val _state = MutableValue(CreateEntry.State(projectDef = projectDef))
	override val state: Value<CreateEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	override fun createEntry(
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