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
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.projectInject
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EncyclopediaComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), Encyclopedia {

	private val _state = MutableValue(Encyclopedia.State(projectDef = projectDef, entryDefs = emptyList()))
	override val state: Value<Encyclopedia.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val entryContentCache = Cache.Builder()
		.maximumCacheSize(20)
		.build<Int, EntryContainer>()

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

	override fun updateFilter(text: String?, type: EntryType?) {
		_state.reduce { state ->
			state.copy(
				filterText = text,
				filterType = type
			)
		}
	}

	override fun getFilteredEntries(): List<EntryDef> {
		val type = state.value.filterType
		val text = state.value.filterText

		return state.value.entryDefs.filter { entry ->
			val typeOk = type == null || entry.type == type
			val textOk = text.isNullOrEmpty() || entry.name.contains(text.trim(), ignoreCase = true)
			typeOk && textOk
		}
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val cachedEntry = entryContentCache.get(entryDef.id)
		return if (cachedEntry != null) {
			cachedEntry.entry
		} else {
			val container = encyclopediaRepository.loadEntry(entryDef)
			entryContentCache.put(entryDef.id, container)
			container.entry
		}
	}

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

	override fun showCreate(show: Boolean) {
		_state.reduce { state ->
			state.copy(
				showCreate = show
			)
		}
	}

	override fun getImagePath(entryDef: EntryDef): String? {
		return if (encyclopediaRepository.hasEntryImage(entryDef, "jpg")) {
			encyclopediaRepository.getEntryImagePath(entryDef, "jpg").path
		} else {
			null
		}
	}
}