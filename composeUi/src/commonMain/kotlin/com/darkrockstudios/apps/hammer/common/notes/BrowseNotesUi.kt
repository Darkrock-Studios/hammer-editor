package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems

private enum class NotesSortMode(
	val labelRes: StringResource,
	val glyphRes: StringResource,
) {
	DateDesc(Res.string.notes_sort_newest, Res.string.notes_sort_glyph_date_desc),
	DateAsc(Res.string.notes_sort_oldest, Res.string.notes_sort_glyph_date_asc),
	WordsDesc(Res.string.notes_sort_longest, Res.string.notes_sort_glyph_words_desc),
	WordsAsc(Res.string.notes_sort_shortest, Res.string.notes_sort_glyph_words_asc),
	TitleAsc(Res.string.notes_sort_title_az, Res.string.notes_sort_glyph_title_az),
}

private fun NoteContent.wordCount(): Int =
	content.split(Regex("\\s+")).count { it.isNotBlank() }

private fun NoteContent.firstLine(): String =
	content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

private fun applySort(notes: List<NoteContent>, mode: NotesSortMode): List<NoteContent> = when (mode) {
	NotesSortMode.DateDesc -> notes.sortedByDescending { it.created }
	NotesSortMode.DateAsc -> notes.sortedBy { it.created }
	NotesSortMode.WordsDesc -> notes.sortedByDescending { it.wordCount() }
	NotesSortMode.WordsAsc -> notes.sortedBy { it.wordCount() }
	NotesSortMode.TitleAsc -> notes.sortedBy { it.firstLine().lowercase() }
}

private fun buildTagIndex(notes: List<NoteContent>): List<Pair<String, Int>> {
	val counts = mutableMapOf<String, Int>()
	for (note in notes) {
		for (tag in note.tags) {
			counts[tag] = (counts[tag] ?: 0) + 1
		}
	}
	return counts.entries
		.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
		.map { it.key to it.value }
}

@OptIn(
	ExperimentalSharedTransitionApi::class,
	ExperimentalLayoutApi::class,
	ExperimentalMaterial3Api::class,
)
@Composable
fun BrowseNotesUi(
	component: BrowseNotes,
	modifier: Modifier = Modifier,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isWide = screen.isWide
	// Three width states, mirroring Encyclopedia's behavior:
	//   Expanded → search + filter share one line
	//   Medium   → search above filter
	//   Compact  → search lives behind a title-row icon toggle
	val isExpanded = screen.windowWidthClass == WindowWidthSizeClass.Expanded

	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortMode by remember { mutableStateOf(NotesSortMode.DateDesc) }
	var activeTags by remember { mutableStateOf<Set<String>>(emptySet()) }

	// On narrow screens the search field can't share a row with the
	// sort menu and tag filter, so we toggle it in place of the section
	// header — same affordance pattern Encyclopedia uses.
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val tagIndex by remember(state.notes) {
		derivedStateOf { buildTagIndex(state.notes) }
	}

	val visibleNotes by remember(state.notes, searchQuery, sortMode, activeTags) {
		derivedStateOf {
			val byText = if (searchQuery.isBlank()) {
				state.notes
			} else {
				state.notes.filter { it.content.contains(searchQuery.trim(), ignoreCase = true) }
			}
			val byTags = if (activeTags.isEmpty()) {
				byText
			} else {
				byText.filter { note -> activeTags.all { it in note.tags } }
			}
			applySort(byTags, sortMode)
		}
	}

	val toggleTag: (String) -> Unit = { tag ->
		activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag
	}
	val clearTags: () -> Unit = { activeTags = emptySet() }

	// `enterAlwaysScrollBehavior` collapses the strip on scroll-down and
	// reveals it as soon as the user scrolls back up — same pattern as
	// Encyclopedia.
	val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
		state = rememberTopAppBarState(),
	)

	Column(
		modifier = modifier
			.fillMaxSize()
			.nestedScroll(scrollBehavior.nestedScrollConnection),
	) {
		// Title row — section header with notes/tags meta. On narrow
		// screens it animates into a search field when the user taps the
		// search icon; on wide the header stays put because the search
		// field lives inline in the toolbar below.
		AnimatedContent(
			targetState = showSearchBar && !isWide,
			modifier = Modifier
				.fillMaxWidth()
				.height(Ui.TOP_BAR_HEIGHT)
				.padding(horizontal = Ui.Padding.XL),
			transitionSpec = {
				fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
			},
			label = "NotesTitleAnim",
		) { searching ->
			if (searching) {
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					HdSearchField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						placeholder = stringResource(Res.string.notes_search_placeholder),
						onClear = { searchQuery = "" },
						clearContentDescription =
							stringResource(Res.string.notes_search_clear),
						modifier = Modifier.weight(1f),
					)
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.notes_search_close.get(),
						tint = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier
							.size(24.dp)
							.clickable {
								showSearchBar = false
								searchQuery = ""
							},
					)
				}
			} else {
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdSectionHeader(
						section = 3,
						title = stringResource(Res.string.notes_header),
						modifier = Modifier.weight(1f),
						trailing = {
							// On narrow screens the meta competes with the
							// search icon for space — only surface the
							// summary when there's room for it.
							if (isWide) {
								HdMonoLabel(
									text = stringResource(
										Res.string.notes_header_meta,
										state.notes.size,
										tagIndex.size,
									),
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

		// Toolbar + tag filter + active filters slide off together on
		// scroll-down and reveal on scroll-up.
		CollapsingStrip(scrollBehavior = scrollBehavior) {
			val searchField: @Composable () -> Unit = {
				HdSearchField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					modifier = Modifier
						.then(if (isExpanded) Modifier.width(280.dp) else Modifier.fillMaxWidth()),
					placeholder = stringResource(Res.string.notes_search_placeholder),
					onClear = { searchQuery = "" },
					clearContentDescription =
						stringResource(Res.string.notes_search_clear),
				)
			}

			// Medium width: search sits on its own row above the filter
			// row. Expanded folds search inline (passed as `leading` to
			// TagFilterBar). Compact omits this row entirely — search
			// lives in the title-row toggle.
			if (isWide && !isExpanded) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
					verticalAlignment = Alignment.CenterVertically,
				) {
					searchField()
				}
			}

			// Tag filter bar always renders so SORT has a stable home —
			// the chips area is empty when no tags exist, but ALL and
			// SORT are still useful affordances.
			TagFilterBar(
				tags = tagIndex,
				total = state.notes.size,
				activeTags = activeTags,
				onToggle = toggleTag,
				onClear = clearTags,
				leading = if (isExpanded) {
					{ searchField() }
				} else {
					null
				},
				trailing = {
					SortMenuButton(
						sortMode = sortMode,
						onSortChange = { sortMode = it },
					)
				},
			)

			AnimatedVisibility(visible = activeTags.isNotEmpty()) {
				ActiveFiltersStrip(
					activeTags = activeTags,
					hits = visibleNotes.size,
					total = state.notes.size,
					onToggle = toggleTag,
					onClear = clearTags,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}

		LazyVerticalStaggeredGrid(
			columns = StaggeredGridCells.Adaptive(400.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			if (visibleNotes.isEmpty()) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = Ui.Padding.XXL),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = stringResource(Res.string.notes_list_empty),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}

			staggeredItems(
				items = visibleNotes,
				key = { note -> note.id },
			) { note ->
				NoteCard(
					note = note,
					activeTags = activeTags,
					onTagClick = toggleTag,
					sharedTransitionScope = sharedTransitionScope,
					animatedVisibilityScope = animatedVisibilityScope,
					modifier = Modifier.padding(bottom = Ui.Padding.L),
					onClick = { component.viewNote(note.id) },
				)
			}
		}
	}
}

/**
 * Translates [content] by [scrollBehavior]'s height offset and reduces
 * the slot's reported height by the same amount, so a sibling scroll
 * container slides up under the disappearing strip. Mirrors the
 * `CollapsingStrip` used in `BrowseEntriesUi`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingStrip(
	scrollBehavior: TopAppBarScrollBehavior,
	content: @Composable () -> Unit,
) {
	Layout(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.clipToBounds(),
		content = content,
	) { measurables, constraints ->
		val placeables = measurables.map { it.measure(constraints) }
		val totalHeight = placeables.sumOf { it.height }
		scrollBehavior.state.heightOffsetLimit = -totalHeight.toFloat()
		val offset = scrollBehavior.state.heightOffset.roundToInt()
		val visibleHeight = (totalHeight + offset).coerceAtLeast(0)
		val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
		layout(width, visibleHeight) {
			var y = offset
			placeables.forEach { placeable ->
				placeable.place(0, y)
				y += placeable.height
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
	note: NoteContent,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val hammerColors = LocalHammerColors.current
	with(sharedTransitionScope) {
		// `wrapContentHeight()` is defensive — staggered grid items already
		// give children unbounded height, but this guarantees the card sizes
		// to its content even when a sibling modifier propagates a max.
		Column(
			modifier = modifier
				.fillMaxWidth()
				.wrapContentHeight()
				.background(MaterialTheme.colorScheme.surfaceContainerLow, RectangleShape)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "note-card-${note.id}"),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.clickable(onClick = onClick),
		) {
			val date = remember(note.created) {
				note.created.toLocalDateTime(TimeZone.currentSystemDefault()).format("dd MMM `yy")
			}
			val words = remember(note.content) { note.wordCount() }
			// Editor-saved notes commonly end with a trailing `\n`; the
			// markdown pipeline splits on newlines, so a trailing newline
			// becomes an empty trailing line and shows up as a blank gap at
			// the bottom of the card. Trim defensively.
			val previewMarkdown = remember(note.content) { note.content.trim() }

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
				verticalAlignment = Alignment.CenterVertically,
			) {
				HdMonoLabel(
					text = date,
					modifier = Modifier.sharedElement(
						sharedContentState = rememberSharedContentState(key = "note-date-${note.id}"),
						animatedVisibilityScope = animatedVisibilityScope,
					),
				)
				Spacer(modifier = Modifier.weight(1f))
				HdMonoLabel(text = stringResource(Res.string.notes_word_count_short, words))
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			MarkdownView(
				markdown = previewMarkdown,
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						horizontal = Ui.Padding.XL,
						vertical = Ui.Padding.L,
					)
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "note-content-${note.id}"),
						animatedVisibilityScope = animatedVisibilityScope,
					),
			)

			if (note.tags.isNotEmpty()) {
				FlowRow(
					modifier = Modifier
						.fillMaxWidth()
						.padding(
							start = Ui.Padding.L,
							end = Ui.Padding.L,
							bottom = Ui.Padding.L,
						),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					note.tags.sorted().forEach { tag ->
						val isActive = tag in activeTags
						HdTagChip(
							label = tag,
							active = isActive,
							accent = if (isActive) hammerColors.colorForCharacter(tag) else null,
							onClick = { onTagClick(tag) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun TagFilterBar(
	tags: List<Pair<String, Int>>,
	total: Int,
	activeTags: Set<String>,
	onToggle: (String) -> Unit,
	onClear: () -> Unit,
	leading: (@Composable () -> Unit)? = null,
	trailing: @Composable () -> Unit = {},
) {
	val hammerColors = LocalHammerColors.current
	val allActive = activeTags.isEmpty()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (leading != null) {
			leading()
			Box(
				modifier = Modifier
					.height(20.dp)
					.width(Dp.Hairline)
					.background(MaterialTheme.colorScheme.outlineVariant),
			)
		}
		AllChip(
			label = "${stringResource(Res.string.notes_filter_all)} · $total",
			active = allActive,
			onClick = onClear,
		)
		Box(
			modifier = Modifier
				.height(20.dp)
				.width(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
		LazyRow(
			modifier = Modifier.weight(1f),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			items(count = tags.size) { i ->
				val (label, count) = tags[i]
				val isActive = label in activeTags
				HdTagChip(
					label = "$label · $count",
					active = isActive,
					accent = if (isActive) hammerColors.colorForCharacter(label) else null,
					onClick = { onToggle(label) },
				)
			}
		}
		trailing()
	}
}

@Composable
private fun AllChip(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
) {
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	Box(
		modifier = Modifier
			.height(28.dp)
			.background(background, RectangleShape)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.L),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = label, color = labelColor)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFiltersStrip(
	activeTags: Set<String>,
	hits: Int,
	total: Int,
	onToggle: (String) -> Unit,
	onClear: () -> Unit,
) {
	val hammerColors = LocalHammerColors.current
	Column(modifier = Modifier.fillMaxWidth()) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.S),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			HdMonoLabel(
				text = stringResource(Res.string.notes_filter_filtered, hits, total),
				modifier = Modifier
					.padding(end = Ui.Padding.S)
					.align(Alignment.CenterVertically),
			)
			activeTags.sorted().forEach { tag ->
				HdTagChip(
					label = tag,
					active = true,
					accent = hammerColors.colorForCharacter(tag),
					onClick = { onToggle(tag) },
					onRemove = { onToggle(tag) },
				)
			}
			Box(
				modifier = Modifier
					.clickable(onClick = onClear)
					.padding(horizontal = Ui.Padding.S, vertical = Ui.Padding.S),
				contentAlignment = Alignment.Center,
			) {
				HdMonoLabel(text = stringResource(Res.string.notes_filter_clear_all))
			}
		}
	}
}

@Composable
private fun SortMenuButton(
	sortMode: NotesSortMode,
	onSortChange: (NotesSortMode) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		Row(
			modifier = Modifier
				.height(32.dp)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.clickable { expanded = true }
				.padding(horizontal = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		) {
			HdMonoLabel(
				text = stringResource(Res.string.notes_sort_label),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			HdMonoLabel(
				text = stringResource(sortMode.glyphRes),
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			NotesSortMode.entries.forEach { mode ->
				DropdownMenuItem(
					text = {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = stringResource(mode.labelRes),
								style = MaterialTheme.typography.bodyMedium,
								color = if (mode == sortMode) {
									MaterialTheme.colorScheme.onSurface
								} else {
									MaterialTheme.colorScheme.onSurfaceVariant
								},
							)
							Spacer(modifier = Modifier.weight(1f))
							Spacer(modifier = Modifier.width(Ui.Padding.XL))
							HdMonoLabel(text = stringResource(mode.glyphRes))
						}
					},
					onClick = {
						onSortChange(mode)
						expanded = false
					},
				)
			}
		}
	}
}

@Composable
fun BrowseNotesFab(
	component: BrowseNotes,
	modifier: Modifier,
) {
	HdFab(
		onClick = { component.showCreate() },
		icon = Icons.Filled.Create,
		contentDescription = Res.string.notes_create_note_button.get(),
		modifier = modifier,
	)
}
