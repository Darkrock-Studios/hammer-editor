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
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
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

	private val _tagIndex = MutableValue(TagIndex.EMPTY)
	override val tagIndex: Value<TagIndex> = _tagIndex

	private val encyclopediaService: EncyclopediaService by projectInject()
	private val tagIndexService: TagIndexService by projectInject()

	init {
		stateKeeper.register(FILTER_KEY, SavedFilter.serializer()) {
			SavedFilter(filterText = _filterText.value, filterType = _state.value.filterType)
		}
	}

	private val entryContentCache = Cache.Builder<Int, EntryContainer>()
		.maximumCacheSize(20)
		.build()

	override fun onCreate() {
		super.onCreate()
		watchEntries()
		watchTagIndex()
	}

	override fun onResume() {
		super.onResume()
		encyclopediaService.loadEntries()
	}

	private fun watchEntries() {
		scope.launch {
			encyclopediaService.entryListFlow.collect { entryDefs ->
				entryContentCache.invalidateAll()

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

	private fun watchTagIndex() {
		scope.launch {
			tagIndexService.tagIndex.collect { index ->
				withContext(dispatcherMain) {
					if (index != _tagIndex.value) _tagIndex.value = index
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

	override fun addTagToSearch(tag: String) {
		val parsed = parseQuery(_filterText.value)
		if (parsed.tags.none { it.equals(tag, ignoreCase = true) }) {
			_filterText.update { "$it #$tag".trim() }
		}
	}

	override fun getFilteredEntries(): List<EntryDef> {
		val type = state.value.filterType
		val parsed = parseQuery(filterText.value)
		val index = tagIndexService.tagIndex.value

		// Name matching ignores whitespace on both sides, so "darkforest" still
		// finds "Dark Forest".
		val searchTerm = parsed.text.filterNot { it.isWhitespace() }

		val idsMatchingAllTags: Set<Int>? = if (parsed.tags.isEmpty()) {
			null
		} else {
			parsed.tags.map { needle -> encyclopediaIdsMatchingTag(needle, index) }
				.reduce { acc, ids -> acc intersect ids }
		}

		return state.value.entryDefs.filter { entry ->
			val typeOk = type == null || entry.type == type
			val textOk = searchTerm.isEmpty() ||
				entry.name.filterNot { it.isWhitespace() }.contains(searchTerm, ignoreCase = true)
			val tagOk = idsMatchingAllTags == null || entry.id in idsMatchingAllTags

			typeOk && textOk && tagOk
		}
	}

	// Substring, case-insensitive tag match against the project's tag universe -
	// same semantics as Global Search and the project list.
	private fun encyclopediaIdsMatchingTag(needle: String, index: TagIndex): Set<Int> =
		index.tagToEntities
			.asSequence()
			.filter { (tag, _) -> tag.contains(needle, ignoreCase = true) }
			.flatMap { (_, refs) -> refs.asSequence() }
			.filter { it.type == TaggedEntityType.Encyclopedia }
			.mapTo(mutableSetOf()) { it.id }

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

	@Serializable
	private data class SavedFilter(
		val filterText: String,
		val filterType: EntryType?,
	)

	private companion object {
		const val FILTER_KEY = "browse-entries-filter"
	}
}
