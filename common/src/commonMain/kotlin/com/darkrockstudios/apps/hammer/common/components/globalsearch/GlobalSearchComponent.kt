package com.darkrockstudios.apps.hammer.common.components.globalsearch

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsearchrepository.GlobalSearchRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject

class GlobalSearchComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val onDismiss: () -> Unit,
	private val navigateToResult: (SearchResult) -> Unit,
	initialQuery: String? = null,
) : ProjectComponentBase(projectDef, componentContext), GlobalSearch {

	private val searchRepository: GlobalSearchRepository by projectInject()

	override val state: Value<GlobalSearch.State>
		get() = searchRepository.state

	init {
		if (!initialQuery.isNullOrBlank()) {
			searchRepository.setQuery(initialQuery)
		}
	}

	override fun onQueryChanged(query: String) {
		searchRepository.setQuery(query)
	}

	override fun onFilterChanged(filter: GlobalSearchFilter) {
		searchRepository.setFilter(filter)
	}

	override fun onResultClicked(result: SearchResult) {
		navigateToResult(result)
	}

	override fun dismiss() {
		onDismiss()
	}
}
