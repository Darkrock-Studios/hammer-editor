package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGrid
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCollapsingStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntryFilterBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntryFilterOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFab
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.encyclopedia_browse_list_empty
import com.darkrockstudios.apps.hammer.encyclopedia_create_button
import com.darkrockstudios.apps.hammer.encyclopedia_header
import com.darkrockstudios.apps.hammer.encyclopedia_search_clear_button
import com.darkrockstudios.apps.hammer.encyclopedia_search_hint
import com.darkrockstudios.apps.hammer.notes_search_button
import com.darkrockstudios.apps.hammer.notes_search_close
import kotlinx.coroutines.CoroutineScope

const val ENCYCLOPEDIA_CREATE_FAB_TAG = "encyclopedia-create-fab"

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseEntriesUi(
	component: BrowseEntries,
	scope: CoroutineScope,
	viewEntry: (EntryDef) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()
	var selectedType by remember(state.filterType) { mutableStateOf(state.filterType) }
	val searchText by component.filterText.subscribeAsState()
	val tagIndex by component.tagIndex.subscribeAsState()

	// `#tag` needles are parsed out of the raw query the same way Global Search
	// and the project list do it; the query field itself keeps the literal text
	// so `#` is typeable and the tags round-trip.
	val activeTags = remember(searchText) { parseQuery(searchText).tags }

	// Keyed on `tagIndex` (not just its counts) so results recompute whenever the
	// async index rebuilds - membership can change without the entry list changing.
	val filteredEntries = remember(state.entryDefs, searchText, state.filterType, tagIndex) {
		component.getFilteredEntries()
	}

	val isWide = LocalScreenCharacteristic.current.isWide

	val onSelectType: (EntryType?) -> Unit = { type ->
		selectedType = type
		component.updateFilter(searchText, type)
	}
	val onSearchTextChange: (String) -> Unit = { text ->
		component.updateFilter(text, selectedType)
	}
	val onClearSearch: () -> Unit = {
		component.updateFilter("", selectedType)
	}

	// `enterAlwaysScrollBehavior` collapses the strip on scroll-down and
	// reveals it as soon as the user scrolls back up — same semantics as
	// an M3 TopAppBar, just translated onto our hairline filter strip.
	val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
		state = rememberTopAppBarState(),
	)

	// On narrow screens the search field can't share a row with the
	// filter bar, so we toggle it in place of the section header — same
	// affordance pattern Notes uses.
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(scrollBehavior.nestedScrollConnection),
	) {
		// Title row — section header with category-count meta. On narrow
		// it animates into a search field when the user taps the search
		// icon; on wide the header stays put because the search field
		// lives inline in the filter strip below.
		AnimatedContent(
			targetState = showSearchBar && !isWide,
			modifier = Modifier
				.fillMaxWidth()
				.height(Ui.TOP_BAR_HEIGHT)
				.padding(horizontal = Ui.Padding.XL),
			transitionSpec = {
				fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
			},
			label = "EncyclopediaTitleAnim",
		) { searching ->
			if (searching) {
				HdSearchRow(
					query = searchText,
					onQueryChange = onSearchTextChange,
					placeholder = Res.string.encyclopedia_search_hint.get(),
					clearContentDescription = Res.string.encyclopedia_search_clear_button.get(),
					onCollapse = { showSearchBar = false },
					collapseContentDescription = Res.string.notes_search_close.get(),
					modifier = Modifier.fillMaxSize(),
					onClear = onClearSearch,
				)
			} else {
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdSectionHeader(
						section = 5,
						title = Res.string.encyclopedia_header.get(),
						modifier = Modifier.weight(1f),
						trailing = {
							// Counts are redundant with the filter strip on
							// narrow screens — only surface the summary
							// when there's room for it on a single line.
							if (isWide) {
								HdMonoLabel(
									text = entrySummary(state.entryDefs),
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						},
					)
					if (!isWide) {
						IconButton(onClick = { showSearchBar = true }) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = Res.string.notes_search_button.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						}
					}
				}
			}
		}

		HdFolioDivider()

		// Filter strip + reflected tag chips, both translated by the scroll
		// behavior's height offset so they slide off together. The search
		// field is included on wide only — narrow screens surface it through
		// the title-row toggle instead.
		HdCollapsingStrip(scrollBehavior = scrollBehavior) {
			FilterStrip(
				searchText = searchText,
				onSearchTextChange = onSearchTextChange,
				onClearSearch = onClearSearch,
				searchClearLabel = Res.string.encyclopedia_search_clear_button.get(),
				searchPlaceholder = Res.string.encyclopedia_search_hint.get(),
				options = remember(state.entryDefs) { buildFilterOptions(state.entryDefs) },
				selectedType = selectedType,
				onSelectType = onSelectType,
				activeTags = activeTags,
				showSearchField = isWide,
				wrap = isWide,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			)
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}

		// Card grid — adaptive 320dp min cell so on wide screens we get
		// 3-4 columns and on narrow screens cards stack into a single
		// column.
		val gridState = rememberLazyGridState()
		Box(modifier = Modifier.weight(1f)) {
			LazyVerticalGrid(
				state = gridState,
				columns = GridCells.Adaptive(minSize = 320.dp),
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
			) {
				if (filteredEntries.isEmpty()) {
					item {
						Text(
							text = Res.string.encyclopedia_browse_list_empty.get(),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onBackground,
							modifier = Modifier.padding(Ui.Padding.XL),
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
							activeTags = activeTags.toSet(),
							tagsScrollHorizontally = !isWide,
							filterByType = onSelectType,
						)
					}
				}
			}

			MpScrollBarGrid(
				modifier = scrollBarOverlay(),
				state = gridState,
			)
		}
	}
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterStrip(
	searchText: String,
	onSearchTextChange: (String) -> Unit,
	onClearSearch: () -> Unit,
	searchClearLabel: String,
	searchPlaceholder: String,
	options: List<HdEntryFilterOption>,
	selectedType: EntryType?,
	onSelectType: (EntryType?) -> Unit,
	activeTags: List<String>,
	showSearchField: Boolean,
	wrap: Boolean,
	modifier: Modifier = Modifier,
) {
	val content: @Composable () -> Unit = {
		if (showSearchField) {
			HdSearchField(
				value = searchText,
				onValueChange = onSearchTextChange,
				placeholder = searchPlaceholder,
				onClear = onClearSearch,
				clearContentDescription = searchClearLabel,
				modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
			)
		}
		HdEntryFilterBar(
			options = options,
			selected = selectedType,
			onSelect = onSelectType,
		)
		// Reflected `#tag` chips — a read-only echo of the tags parsed out of
		// the query, cleared by editing the query text (same as Global Search
		// and the project list).
		activeTags.forEach { tag ->
			HdTagChip(label = tag, active = true)
		}
	}
	if (wrap) {
		FlowRow(
			modifier = modifier,
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			content()
		}
	} else {
		Row(
			modifier = modifier.horizontalScroll(rememberScrollState()),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			content()
		}
	}
}

private fun buildFilterOptions(entries: List<EntryDef>): List<HdEntryFilterOption> {
	val total = entries.size
	val byType = entries.groupingBy { it.type }.eachCount()
	val ordered = listOf(
		EntryType.PERSON,
		EntryType.PLACE,
		EntryType.THING,
		EntryType.EVENT,
		EntryType.IDEA,
	)
	return buildList {
		add(HdEntryFilterOption(type = null, label = "ALL", count = total))
		ordered.forEach { type ->
			val count = byType[type] ?: 0
			if (count > 0) {
				add(
					HdEntryFilterOption(
						type = type,
						label = type.text.uppercase() + "S",
						count = count,
					),
				)
			}
		}
	}
}

private fun entrySummary(entries: List<EntryDef>): String {
	if (entries.isEmpty()) return "0 entries"
	val byType = entries.groupingBy { it.type }.eachCount()
	val total = entries.size
	val parts = mutableListOf("$total ENTRIES")
	val ordered = listOf(
		EntryType.PERSON,
		EntryType.PLACE,
		EntryType.THING,
		EntryType.EVENT,
		EntryType.IDEA,
	)
	ordered.forEach { type ->
		val count = byType[type] ?: 0
		if (count > 0) {
			parts.add("$count ${type.text.uppercase()}${if (count == 1) "" else "S"}")
		}
	}
	return parts.joinToString(" · ")
}

@Composable
fun BrowseEntriesFab(
	component: Encyclopedia,
	modifier: Modifier,
) {
	val stack by component.stack.subscribeAsState()
	when (stack.active.instance) {
		is Encyclopedia.Destination.BrowseEntriesDestination -> {
			HdFab(
				onClick = component::showCreateEntry,
				icon = Icons.Default.Create,
				contentDescription = Res.string.encyclopedia_create_button.get(),
				modifier = modifier.testTag(ENCYCLOPEDIA_CREATE_FAB_TAG),
			)
		}

		is Encyclopedia.Destination.ViewEntryDestination -> Unit
		is Encyclopedia.Destination.CreateEntryDestination -> Unit
	}
}
