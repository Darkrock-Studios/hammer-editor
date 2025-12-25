package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moveFocusOnTab
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BrowseEntriesUi(
	component: BrowseEntries,
	scope: CoroutineScope,
	viewEntry: (EntryDef) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()
	val types = remember { EntryType.entries }
	var selectedType by remember(state.filterType) { mutableStateOf(state.filterType) }
	val searchText by component.filterText.subscribeAsState()

	val filteredEntries by remember(
		Triple(
			state.entryDefs,
			searchText,
			state.filterType
		)
	) { mutableStateOf(component.getFilteredEntries()) }

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.Padding.XL),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceAround,
		) {
			EntrySearchBar(searchText, component, selectedType)

			ExposedDropDown(
				getText = { it.toStringResource().get() },
				label = Res.string.encyclopedia_filter_by_category.get(),
				modifier = Modifier.width(128.dp).moveFocusOnTab(),
				items = types,
				noneOption = Res.string.encyclopedia_category_all.get(),
				selectedItem = state.filterType
			) { item ->
				selectedType = item
				component.updateFilter(searchText, selectedType)
			}
		}

		LazyVerticalGrid(
			columns = GridCells.Adaptive(480.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(Ui.Padding.XL)
		) {
			if (filteredEntries.isEmpty()) {
				item {
					Text(
						Res.string.encyclopedia_browse_list_empty.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground
					)
				}
			} else {
				items(filteredEntries.size) { index ->
					EncyclopediaEntryItem(
						entryDef = filteredEntries[index],
						component = component,
						viewEntry = viewEntry,
						scope = scope,
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
					) { type ->
						selectedType = type
						component.updateFilter(searchText, type)
					}
				}
			}
		}
	}
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RowScope.EntrySearchBar(
	searchText: String,
	component: BrowseEntries,
	selectedType: EntryType?
) {
	val onActiveChange = { b: Boolean -> }
	SearchBar(
		inputField = {
			SearchBarDefaults.InputField(
				query = searchText,
				onQueryChange = { component.updateFilter(it, selectedType) },
				onSearch = { component.updateFilter(it, selectedType) },
				expanded = false,
				onExpandedChange = onActiveChange,
				enabled = true,
				placeholder = { Text(Res.string.encyclopedia_search_hint.get()) },
				leadingIcon = null,
				trailingIcon = {
					IconButton(onClick = {
						component.clearFilterText()
					}) {
						Icon(
							imageVector = Icons.Filled.Clear,
							Res.string.encyclopedia_search_clear_button.get()
						)
					}
				},
				colors = SearchBarDefaults.colors().inputFieldColors,
				interactionSource = null,
			)
		},
		expanded = false,
		onExpandedChange = onActiveChange,
		modifier = Modifier.moveFocusOnTab().weight(1f).padding(end = Ui.Padding.L),
		shape = SearchBarDefaults.inputFieldShape,
		colors = SearchBarDefaults.colors(),
		tonalElevation = SearchBarDefaults.TonalElevation,
		shadowElevation = SearchBarDefaults.ShadowElevation,
		windowInsets = SearchBarDefaults.windowInsets,
		content = {
		},
	)
}

@Composable
fun BrowseEntriesFab(
	component: Encyclopedia,
	modifier: Modifier,
) {
	val stack by component.stack.subscribeAsState()
	when (stack.active.instance) {
		is Encyclopedia.Destination.BrowseEntriesDestination -> {
			FloatingActionButton(
				modifier = modifier,
				onClick = component::showCreateEntry,
			) {
				Icon(Icons.Default.Create, Res.string.timeline_create_event_button.get())
			}
		}

		is Encyclopedia.Destination.ViewEntryDestination -> {

		}

		is Encyclopedia.Destination.CreateEntryDestination -> {

		}
	}
}
