package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import kotlinx.coroutines.withContext

class CreateEntryComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), CreateEntry {

	private val _state = MutableValue(CreateEntry.State(projectDef = projectDef))
	override val state: Value<CreateEntry.State> = _state

	private val encyclopediaService: EncyclopediaService by projectInject()
	private val backfillEntryReferences: BackfillEntryReferencesUseCase by projectInject()

	// Note: Back handler is disabled to allow predictive back animation.
	// The UI handles close confirmation via confirmClose()/dismissConfirmClose().

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
		tags: Set<String>,
		imagePath: String?
	): EntryResult = withContext(dispatcherDefault) {
		val result = encyclopediaService.createEntry(name, type, text, tags, imagePath)
		if (result.error == EntryError.NONE) {
			encyclopediaService.loadEntries()
			result.instance?.entry?.let { newEntry ->
				backfillEntryReferences(newEntry)
			}
		}

		result
	}
}