package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.moveFocusOnTab
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier.padding(Ui.Padding.XL),
			verticalAlignment = Alignment.CenterVertically
		) {
			OutlinedTextField(
				value = searchText,
				onValueChange = {
					searchText = it
					component.updateFilter(searchText, selectedType)
				},
				label = { Text(MR.strings.encyclopedia_search_hint.get()) },
				singleLine = true,
				placeholder = { Text(MR.strings.encyclopedia_search_hint.get()) },
				modifier = Modifier.moveFocusOnTab().weight(1f),
				keyboardOptions = KeyboardOptions(
					autoCorrect = false,
					imeAction = ImeAction.Done,
					keyboardType = KeyboardType.Password
				),
				trailingIcon = {
					IconButton(onClick = {
						searchText = ""
						component.updateFilter(searchText, selectedType)
					}) {
						Icon(imageVector = Icons.Filled.Clear, MR.strings.encyclopedia_search_clear_button.get())
					}
				},
			)

			Spacer(Modifier.width(Ui.Padding.XL))

			ExposedDropDown(
				modifier = Modifier.defaultMinSize(minWidth = 128.dp),
				padding = Ui.Padding.XL,
				items = types,
				noneOption = MR.strings.encyclopedia_category_all.get(),
				defaultIndex = state.filterType?.let { types.indexOf(state.filterType) + 1 } ?: 0
			) { item ->
				selectedType = item
				component.updateFilter(searchText, selectedType)
			}
		}

		LazyVerticalStaggeredGrid(
			columns = StaggeredGridCells.Adaptive(512.dp),
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

	FloatingActionButton(
		onClick = showCreate,
		modifier = Modifier.align(Alignment.BottomEnd).padding(Ui.Padding.XL)
	) {
		Icon(
			Icons.Rounded.Add,
			MR.strings.encyclopedia_create_button.get()
		)
	}
}
