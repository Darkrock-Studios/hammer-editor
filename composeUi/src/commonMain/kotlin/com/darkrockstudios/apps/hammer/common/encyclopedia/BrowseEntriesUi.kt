package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
internal fun BoxWithConstraintsScope.BrowseEntriesUi(
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
			modifier = Modifier.padding(horizontal = Ui.Padding.XL),
			verticalAlignment = Alignment.CenterVertically
		) {
			OutlinedTextField(
				value = searchText,
				onValueChange = {
					component.updateFilter(it, selectedType)
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
						component.clearFilterText()
					}) {
						Icon(
							imageVector = Icons.Filled.Clear,
							MR.strings.encyclopedia_search_clear_button.get()
						)
					}
				},
			)

			Spacer(Modifier.width(Ui.Padding.XL))

			ExposedDropDown(
				getText = { strRes.get(it.toStringResource()) },
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