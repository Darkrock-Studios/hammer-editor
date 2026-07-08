package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
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
import kotlinx.serialization.Serializable

class BrowseEntriesComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef
) : ProjectComponentBase(projectDef, componentContext), BrowseEntries {

	private val restoredFilter: SavedFilter? =
		stateKeeper.consume(FILTER_KEY, SavedFilter.serializer())

	private val _state = MutableValue(BrowseEntries.State(filterType = restoredFilter?.filterType))
	override val state: Value<BrowseEntries.State> = _state

	private val _filterText = MutableValue(restoredFilter?.filterText ?: "")
	override val filterText: Value<String> = _filterText

	private val encyclopediaService: EncyclopediaService by projectInject()

	init {
		stateKeeper.register(FILTER_KEY, SavedFilter.serializer()) {
			SavedFilter(filterText = _filterText.value, filterType = _state.value.filterType)
		}
	}

	private val entryContentCache = Cache.Builder<Int, EntryContainer>()
		.maximumCacheSize(20)
		.build()
	private val indexByTag = mutableMapOf<String, MutableSet<Int>>()

	override fun onCreate() {
		super.onCreate()
		watchEntries()
	}

	override fun onResume() {
		super.onResume()
		encyclopediaService.loadEntries()
	}

	private fun reindexEntries(entryDefs: List<EntryDef>) {
		indexByTag.clear()
		entryDefs.forEach { entryDef ->
			val entryContainer = encyclopediaService.loadEntry(entryDef)
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
			encyclopediaService.entryListFlow.collect { entryDefs ->
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
				filterType = type
			)
		}
		_filterText.update { text ?: "" }
	}

	private val hashtagRegex = Regex("""#(\w+)""")
	override fun getFilteredEntries(): List<EntryDef> {
		val type = state.value.filterType
		val text = filterText.value

		val tags = hashtagRegex.findAll(text).map {
			it.groupValues[1]
		}.filter { it.isNotBlank() }.toSet()

		// Remove hashtags
		var searchTerms = text
		tags.forEach {
			searchTerms = searchTerms.replace("#$it", "")
		}

		// Remove any remaining empty hashtags
		searchTerms = searchTerms.replace("#", "")

		// Remove all whitespace
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
				tags.any { tag ->
					val partialTag = indexByTag.keys.any { curTag ->
						curTag.startsWith(tag, true) && (indexByTag[curTag]?.contains(entry.id) == true)
					}

					val exactMatch = (indexByTag[tag]?.contains(entry.id) == true)

					partialTag || exactMatch
				}
			}

			typeOk && textOk && tagOk
		}
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val cachedEntry = entryContentCache.get(entryDef.id)
		return if (cachedEntry != null) {
			cachedEntry.entry
		} else {
			try {
				val container = encyclopediaService.loadEntry(entryDef)
				entryContentCache.put(entryDef.id, container)
				container.entry
			} catch (e: EntryLoadError) {
				Napier.w("Failed to load encyclopedia entry: ${entryDef.id} - ${entryDef.name}", e)
				EntryContent(
					id = entryDef.id,
					name = entryDef.name,
					type = entryDef.type,
					text = "ERROR",
					tags = emptySet()
				)
			}
		}
	}

	override fun getImagePath(entryDef: EntryDef): String? {
		return encyclopediaService.findEntryImagePath(entryDef)?.path
	}

	override suspend fun calculateEntryImageHash(entryDef: EntryDef): String? {
		return encyclopediaService.findEntryImageExtension(entryDef)
			?.let { ext -> encyclopediaService.calculateEntryImageHash(entryDef, ext) }
	}

	override fun clearFilterText() {
		_filterText.update { "" }
	}

	override fun addTagToSearch(tag: String) {
		_filterText.update { "${filterText.value} #$tag" }
	}

	@Serializable
	private data class SavedFilter(
		val filterText: String,
		val filterType: EntryType?,
	)

	private companion object {
		const val FILTER_KEY = "browse-entries-filter"
	}
}