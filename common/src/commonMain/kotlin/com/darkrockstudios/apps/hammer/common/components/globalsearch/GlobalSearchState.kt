package com.darkrockstudios.apps.hammer.common.components.globalsearch

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.darkrockstudios.apps.hammer.common.data.globalsearch.ParsedQuery
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase.Companion.parseQuery
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

/** The slice of search state worth persisting across process death; results re-derive from these. */
@Serializable
data class GlobalSearchSavedState(
	val query: String = "",
	val filter: GlobalSearchFilter = GlobalSearchFilter.All,
)

/**
 * Holds the global-search presentation state and drives the (stateless) [SearchProjectUseCase].
 *
 * Owned by a long-lived parent component (ProjectRoot) and observed by the transient search modal,
 * so the query and results survive the modal being dismissed and reopened. The [scope] must outlive
 * the modal. Process-death survival is handled by the owner persisting [query]/[filter] and passing
 * them back in as the initial values.
 */
class GlobalSearchState(
	private val searchProjectUseCase: SearchProjectUseCase,
	mainContext: CoroutineContext,
	initialQuery: String = "",
	initialFilter: GlobalSearchFilter = GlobalSearchFilter.All,
) : InstanceKeeper.Instance {

	// Own scope so an in-flight search survives the modal being dismissed; cancelled in onDestroy.
	// Runs on the main context so MutableValue updates stay on the main thread; the use case
	// offloads its own heavy work.
	private val scope = CoroutineScope(mainContext + SupervisorJob())

	private val _state = MutableValue(buildInitialState(initialQuery, initialFilter))
	val state: Value<GlobalSearch.State> = _state

	private var searchJob: Job? = null

	init {
		val parsed = parseQuery(initialQuery)
		if (parsed.isUsable()) {
			startSearch(initialQuery, parsed, initialFilter, debounce = false)
		}
	}

	fun setQuery(query: String) {
		val parsed = parseQuery(query)
		_state.getAndUpdate {
			it.copy(query = query, parsedText = parsed.text, parsedTags = parsed.tags)
		}
		startSearch(query, parsed, _state.value.filter, debounce = true)
	}

	fun setFilter(filter: GlobalSearchFilter) {
		if (_state.value.filter == filter) return
		_state.getAndUpdate { it.copy(filter = filter) }
		val query = _state.value.query
		startSearch(query, parseQuery(query), filter, debounce = false)
	}

	private fun startSearch(
		query: String,
		parsed: ParsedQuery,
		filter: GlobalSearchFilter,
		debounce: Boolean,
	) {
		searchJob?.cancel()

		if (!parsed.isUsable()) {
			_state.getAndUpdate { it.copy(isSearching = false, results = emptyList()) }
			return
		}

		searchJob = scope.launch {
			try {
				if (debounce) delay(DEBOUNCE_MS)
				_state.getAndUpdate { it.copy(isSearching = true) }
				val results = searchProjectUseCase.search(query, filter)
				_state.getAndUpdate { it.copy(isSearching = false, results = results) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Global search failed", e)
				_state.getAndUpdate { it.copy(isSearching = false) }
			}
		}
	}

	override fun onDestroy() {
		scope.cancel()
	}

	private fun buildInitialState(query: String, filter: GlobalSearchFilter): GlobalSearch.State {
		val parsed = parseQuery(query)
		return GlobalSearch.State(
			query = query,
			parsedText = parsed.text,
			parsedTags = parsed.tags,
			filter = filter,
		)
	}

	companion object {
		const val DEBOUNCE_MS = 250L
	}
}
