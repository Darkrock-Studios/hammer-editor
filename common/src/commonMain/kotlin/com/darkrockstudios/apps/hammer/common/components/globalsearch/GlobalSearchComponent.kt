package com.darkrockstudios.apps.hammer.common.components.globalsearch

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class GlobalSearchComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val searchState: GlobalSearchState,
	private val onDismiss: () -> Unit,
	private val navigateToResult: (SearchResult) -> Unit,
	initialQuery: String? = null,
) : ProjectComponentBase(projectDef, componentContext), GlobalSearch {

	override val state: Value<GlobalSearch.State>
		get() = searchState.state

	init {
		if (!initialQuery.isNullOrBlank()) {
			searchState.setQuery(initialQuery)
		}
	}

	override fun onQueryChanged(query: String) {
		searchState.setQuery(query)
	}

	override fun onFilterChanged(filter: GlobalSearchFilter) {
		searchState.setFilter(filter)
	}

	override fun onResultClicked(result: SearchResult) {
		navigateToResult(result)
	}

	override fun dismiss() {
		onDismiss()
	}
}
