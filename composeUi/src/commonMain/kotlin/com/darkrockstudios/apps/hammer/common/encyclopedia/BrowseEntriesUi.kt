package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxWithConstraintsScope.BrowseEntriesUi(
	component: BrowseEntries,
	scope: CoroutineScope,
	showCreate: () -> Unit,
	viewEntry: (EntryDef) -> Unit
) {
	val state by component.state.subscribeAsState()
	val types = remember { EntryType.values().toList() }
	var selectedType by remember(state.filterType) { mutableStateOf(state.filterType) }
	var searchText by remember { mutableStateOf(state.filterText ?: "") }

	val filteredEntries by remember(
		Triple(
			state.entryDefs,
			state.filterText,
			state.filterType
		)
	) { mutableStateOf(component.getFilteredEntries()) }

	Column(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		Row(verticalAlignment = Alignment.CenterVertically) {
			TextField(
				value = searchText,
				onValueChange = {
					searchText = it
					component.updateFilter(searchText, selectedType)
				},
				placeholder = { Text("Search by Name") },
				modifier = Modifier.weight(1f)
			)

			Spacer(Modifier.width(Ui.Padding.XL))

			ExposedDropDown(
				modifier = Modifier.defaultMinSize(minWidth = 128.dp),
				padding = Ui.Padding.XL,
				items = types,
				noneOption = "All",
				defaultIndex = state.filterType?.let { types.indexOf(state.filterType) + 1 } ?: 0
			) { item ->
				selectedType = item
				component.updateFilter(searchText, selectedType)
			}
		}

		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		LazyVerticalGrid(
			columns = GridCells.Adaptive(512.dp),
			modifier = Modifier.fillMaxWidth(),
			contentPadding = PaddingValues(Ui.Padding.XL)
		) {
			if (filteredEntries.isEmpty()) {
				item {
					Text("No Entries Found")
				}
			} else {
				items(filteredEntries.size) { index ->
					EncyclopediaEntryItem(
						entryDef = filteredEntries[index],
						component = component,
						viewEntry = viewEntry,
						scope = scope,
					)
				}
			}
		}
	}
	FloatingActionButton(
		onClick = showCreate,
		modifier = Modifier.align(Alignment.BottomEnd)
	) {
		Icon(
			Icons.Rounded.Add,
			"Create Entry"
		)
	}
}
