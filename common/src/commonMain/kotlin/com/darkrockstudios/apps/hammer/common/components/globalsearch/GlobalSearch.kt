package com.darkrockstudios.apps.hammer.common.components.globalsearch

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

interface GlobalSearch {
	val state: Value<State>

	fun onQueryChanged(query: String)
	fun onFilterChanged(filter: GlobalSearchFilter)
	fun onResultClicked(result: SearchResult)
	fun dismiss()

	data class State(
		val query: String = "",
		val filter: GlobalSearchFilter = GlobalSearchFilter.All,
		val isSearching: Boolean = false,
		val results: List<SearchResult> = emptyList(),
	)
}

enum class GlobalSearchFilter {
	All,
	Scenes,
	Notes,
	Encyclopedia,
	Timeline,
}

data class AnnotatedSnippet(
	val text: String,
	val matchStart: Int,
	val matchEnd: Int,
)

sealed class SearchResult {
	abstract val title: String
	abstract val snippet: AnnotatedSnippet

	data class Scene(
		val sceneItem: SceneItem,
		override val title: String,
		override val snippet: AnnotatedSnippet,
	) : SearchResult()

	data class Note(
		val noteId: Int,
		override val title: String,
		override val snippet: AnnotatedSnippet,
	) : SearchResult()

	data class EncyclopediaEntry(
		val entryDef: EntryDef,
		override val title: String,
		override val snippet: AnnotatedSnippet,
	) : SearchResult()

	data class TimelineEvent(
		val eventId: Int,
		override val title: String,
		override val snippet: AnnotatedSnippet,
	) : SearchResult()
}
