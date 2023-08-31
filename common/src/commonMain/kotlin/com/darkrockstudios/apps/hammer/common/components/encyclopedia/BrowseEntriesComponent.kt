package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryLoadError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import io.github.aakira.napier.Napier
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseEntriesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), BrowseEntries {

	private val _state = MutableValue(BrowseEntries.State())
	override val state: Value<BrowseEntries.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val entryContentCache = Cache.Builder()
		.maximumCacheSize(20)
		.build<Int, EntryContainer>()
	private val indexByTag = mutableMapOf<String, MutableSet<Int>>()

	override fun onCreate() {
		super.onCreate()
		watchEntries()
	}

	override fun onResume() {
		super.onResume()
		encyclopediaRepository.loadEntries()
	}

	private fun reindexEntries(entryDefs: List<EntryDef>) {
		indexByTag.clear()
		entryDefs.forEach { entryDef ->
			val entryContainer = encyclopediaRepository.loadEntry(entryDef)
			entryContainer.entry.tags.forEach { tag ->
				val ids = indexByTag[tag]
				if (ids == null) {
					indexByTag[tag] = mutableSetOf(entryDef.id)
				} else {
					ids.add(entryDef.id)
				}
			}
		}
	}

	private fun watchEntries() {
		scope.launch {
			encyclopediaRepository.entryListFlow.collect { entryDefs ->
				entryContentCache.invalidateAll()
				reindexEntries(entryDefs)

				withContext(dispatcherMain) {
					_state.getAndUpdate { state ->
						state.copy(
							entryDefs = entryDefs
						)
					}
				}
			}
		}
	}

	override fun updateFilter(text: String?, type: EntryType?) {
		_state.getAndUpdate { state ->
			state.copy(
				filterText = text,
				filterType = type
			)
		}
	}

	private val hashtagRegex = Regex("""#(\w+)""")
	override fun getFilteredEntries(): List<EntryDef> {
		val type = state.value.filterType
		val text = state.value.filterText ?: ""

		val tags = hashtagRegex.findAll(text).map {
			it.groupValues[1]
		}.toSet()

		// Remove hashtags
		var searchTerms = text
		tags.forEach {
			searchTerms = searchTerms.replace("#$it", "")
		}
		// Remove all white space
		searchTerms = searchTerms.replace(" ", "")

		return state.value.entryDefs.filter { entry ->
			val typeOk = (type == null || entry.type == type)
			val cleanedName = entry.name.replace(" ", "")

			val textOk = searchTerms.isBlank() || (
				searchTerms.isNotBlank() &&
					cleanedName.contains(
						searchTerms.trim(),
						ignoreCase = true
					)
				)

			val tagOk = if (tags.isEmpty()) {
				true
			} else {
				tags.any { tag -> (indexByTag[tag]?.contains(entry.id) == true) }
			}

			typeOk && (textOk && tagOk)
		}
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val cachedEntry = entryContentCache.get(entryDef.id)
		return if (cachedEntry != null) {
			cachedEntry.entry
		} else {
			try {
				val container = encyclopediaRepository.loadEntry(entryDef)
				entryContentCache.put(entryDef.id, container)
				container.entry
			} catch (e: EntryLoadError) {
				Napier.w("Failed to load encyclopedia entry: ${entryDef.id} - ${entryDef.name}")
				EntryContent(
					id = entryDef.id,
					name = entryDef.name,
					type = entryDef.type,
					text = "ERROR",
					tags = emptyList()
				)
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
}