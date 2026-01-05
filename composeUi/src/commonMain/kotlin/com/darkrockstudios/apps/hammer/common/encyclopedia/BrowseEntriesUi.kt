package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
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
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val filteredEntries by remember(
		Triple(
			state.entryDefs,
			searchText,
			state.filterType
		)
	) { mutableStateOf(component.getFilteredEntries()) }

	Column(modifier = Modifier.fillMaxSize()) {
		AnimatedContent(
			targetState = showSearchBar,
			modifier = Modifier.fillMaxWidth().height(Ui.TOP_BAR_HEIGHT).padding(horizontal = Ui.Padding.XL),
			transitionSpec = {
				fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
			},
			contentAlignment = Alignment.Center,
			label = "SearchBarAnimation"
		) { isSearchVisible ->
			if (isSearchVisible) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					EntrySearchBar(
						searchText = searchText,
						component = component,
						selectedType = selectedType,
						types = types,
						onTypeSelected = { item ->
							selectedType = item
							component.updateFilter(searchText, item)
						},
						modifier = Modifier.weight(1f)
					)

					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.notes_search_close.get(),
						tint = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier
							.size(24.dp)
							.clickable { showSearchBar = false }
					)
				}
			} else {
				Row(
					modifier = Modifier.fillMaxWidth().fillMaxHeight(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					HeaderUi(Res.string.encyclopedia_header, "\uD83D\uDCDA")

					IconButton(onClick = { showSearchBar = true }) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = Res.string.notes_search_button.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
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
private fun EntrySearchBar(
	searchText: String,
	component: BrowseEntries,
	selectedType: EntryType?,
	types: List<EntryType>,
	onTypeSelected: (EntryType?) -> Unit,
	modifier: Modifier = Modifier
) {
	var filterMenuExpanded by remember { mutableStateOf(false) }
	val onActiveChange = { _: Boolean -> }

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
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp)
					) {
						// Filter chip
						Box {
							FilterChip(
								selected = selectedType != null,
								onClick = { filterMenuExpanded = true },
								label = {
									Text(
										text = selectedType?.toStringResource()?.get()
											?: Res.string.encyclopedia_category_all.get(),
										style = MaterialTheme.typography.labelSmall
									)
								},
								modifier = Modifier.height(28.dp).padding(horizontal = Ui.Padding.M)
							)
							DropdownMenu(
								expanded = filterMenuExpanded,
								onDismissRequest = { filterMenuExpanded = false }
							) {
								// "All" option
								DropdownMenuItem(
									text = { Text(Res.string.encyclopedia_category_all.get()) },
									onClick = {
										onTypeSelected(null)
										filterMenuExpanded = false
									},
									leadingIcon = if (selectedType == null) {
										{
											Icon(
												Icons.Default.Search,
												contentDescription = null,
												modifier = Modifier.size(16.dp)
											)
										}
									} else null
								)
								// Type options
								types.forEach { type ->
									DropdownMenuItem(
										text = { Text(type.toStringResource().get()) },
										onClick = {
											onTypeSelected(type)
											filterMenuExpanded = false
										},
										leadingIcon = if (selectedType == type) {
											{
												Icon(
													Icons.Default.Search,
													contentDescription = null,
													modifier = Modifier.size(16.dp)
												)
											}
										} else null
									)
								}
							}
						}

						// Clear search button
						if (searchText.isNotEmpty()) {
							IconButton(
								onClick = { component.clearFilterText() },
								modifier = Modifier.size(32.dp)
							) {
								Icon(
									imageVector = Icons.Filled.Clear,
									contentDescription = Res.string.encyclopedia_search_clear_button.get(),
									modifier = Modifier.size(18.dp)
								)
							}
						}
					}
				},
				colors = SearchBarDefaults.colors().inputFieldColors,
				interactionSource = null,
			)
		},
		expanded = false,
		onExpandedChange = onActiveChange,
		modifier = modifier.moveFocusOnTab(),
		shape = SearchBarDefaults.inputFieldShape,
		colors = SearchBarDefaults.colors(),
		tonalElevation = SearchBarDefaults.TonalElevation,
		shadowElevation = SearchBarDefaults.ShadowElevation,
		windowInsets = SearchBarDefaults.windowInsets,
		content = {},
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
