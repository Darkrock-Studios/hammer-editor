package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.moveFocusOnTab
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseEntriesUi(
	component: BrowseEntries,
	scope: CoroutineScope,
	viewEntry: (EntryDef) -> Unit
) {
	val state by component.state.subscribeAsState()
	val types = remember { EntryType.entries }
	var selectedType by remember(state.filterType) { mutableStateOf(state.filterType) }
	val searchText by component.filterText.subscribeAsState()
	val strRes = rememberStrRes()

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
			SearchBar(
				query = searchText,
				onQueryChange = { component.updateFilter(it, selectedType) },
				onSearch = { component.updateFilter(it, selectedType) },
				placeholder = { Text(MR.strings.encyclopedia_search_hint.get()) },
				active = false,
				onActiveChange = {},
				modifier = Modifier.moveFocusOnTab().weight(1f).padding(end = Ui.Padding.L),
				trailingIcon = {
					IconButton(onClick = {
						component.clearFilterText()
					}) {
						Icon(
							imageVector = Icons.Filled.Clear,
							MR.strings.encyclopedia_search_clear_button.get()
						)
					}
				},
			) {}

			ExposedDropDown(
				getText = { strRes.get(it.toStringResource()) },
				label = MR.strings.encyclopedia_filter_by_category.get(),
				modifier = Modifier.width(128.dp).moveFocusOnTab(),
				items = types,
				noneOption = MR.strings.encyclopedia_category_all.get(),
				selectedItem = state.filterType
			) { item ->
				selectedType = item
				component.updateFilter(searchText, selectedType)
			}
		}

		LazyVerticalStaggeredGrid(
			columns = StaggeredGridCells.Adaptive(480.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(Ui.Padding.XL)
		) {
			if (filteredEntries.isEmpty()) {
				item {
					Text(
						MR.strings.encyclopedia_browse_list_empty.get(),
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
				Icon(Icons.Default.Create, MR.strings.timeline_create_event_button.get())
			}
		}

		is Encyclopedia.Destination.ViewEntryDestination -> {

		}

		is Encyclopedia.Destination.CreateEntryDestination -> {

		}
	}
}