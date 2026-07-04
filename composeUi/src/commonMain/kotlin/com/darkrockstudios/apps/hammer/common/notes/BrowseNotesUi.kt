package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCollapsingStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFab
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMarkdownCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.firstNonBlankLine
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.notes_create_note_button
import com.darkrockstudios.apps.hammer.notes_filter_all
import com.darkrockstudios.apps.hammer.notes_filter_clear_all
import com.darkrockstudios.apps.hammer.notes_filter_filtered
import com.darkrockstudios.apps.hammer.notes_header
import com.darkrockstudios.apps.hammer.notes_header_meta
import com.darkrockstudios.apps.hammer.notes_list_empty
import com.darkrockstudios.apps.hammer.notes_search_button
import com.darkrockstudios.apps.hammer.notes_search_clear
import com.darkrockstudios.apps.hammer.notes_search_close
import com.darkrockstudios.apps.hammer.notes_search_placeholder
import com.darkrockstudios.apps.hammer.notes_sort_glyph_date_asc
import com.darkrockstudios.apps.hammer.notes_sort_glyph_date_desc
import com.darkrockstudios.apps.hammer.notes_sort_glyph_title_az
import com.darkrockstudios.apps.hammer.notes_sort_glyph_words_asc
import com.darkrockstudios.apps.hammer.notes_sort_glyph_words_desc
import com.darkrockstudios.apps.hammer.notes_sort_label
import com.darkrockstudios.apps.hammer.notes_sort_longest
import com.darkrockstudios.apps.hammer.notes_sort_newest
import com.darkrockstudios.apps.hammer.notes_sort_oldest
import com.darkrockstudios.apps.hammer.notes_sort_shortest
import com.darkrockstudios.apps.hammer.notes_sort_title_az
import com.darkrockstudios.apps.hammer.notes_word_count_short
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems

const val NOTES_CREATE_FAB_TAG = "notes-create-fab"
fun noteCardTag(id: Int) = "note-card-$id"

private enum class NotesSortMode(
	override val labelRes: StringResource,
	override val glyphRes: StringResource,
) : HdSortOption {
	DateDesc(Res.string.notes_sort_newest, Res.string.notes_sort_glyph_date_desc),
	DateAsc(Res.string.notes_sort_oldest, Res.string.notes_sort_glyph_date_asc),
	WordsDesc(Res.string.notes_sort_longest, Res.string.notes_sort_glyph_words_desc),
	WordsAsc(Res.string.notes_sort_shortest, Res.string.notes_sort_glyph_words_asc),
	TitleAsc(Res.string.notes_sort_title_az, Res.string.notes_sort_glyph_title_az),
}

private fun NoteContent.wordCount(): Int =
	content.split(Regex("\\s+")).count { it.isNotBlank() }

private fun NoteContent.firstLine(): String = content.firstNonBlankLine()

private fun applySort(notes: List<NoteContent>, mode: NotesSortMode): List<NoteContent> = when (mode) {
	NotesSortMode.DateDesc -> notes.sortedByDescending { it.created }
	NotesSortMode.DateAsc -> notes.sortedBy { it.created }
	NotesSortMode.WordsDesc -> notes.sortedByDescending { it.wordCount() }
	NotesSortMode.WordsAsc -> notes.sortedBy { it.wordCount() }
	NotesSortMode.TitleAsc -> notes.sortedBy { it.firstLine().lowercase() }
}

@OptIn(
	ExperimentalSharedTransitionApi::class,
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
	val tagIndex by component.rankedTags.subscribeAsState()
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
				HdSearchRow(
					query = searchQuery,
					onQueryChange = { searchQuery = it },
					placeholder = stringResource(Res.string.notes_search_placeholder),
					clearContentDescription = stringResource(Res.string.notes_search_clear),
					onCollapse = {
						showSearchBar = false
						searchQuery = ""
					},
					collapseContentDescription = Res.string.notes_search_close.get(),
					modifier = Modifier.fillMaxSize(),
				)
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
		HdCollapsingStrip(scrollBehavior = scrollBehavior) {
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
			HdTagFilterBar(
				tags = tagIndex,
				allLabel = "${stringResource(Res.string.notes_filter_all)} · ${state.notes.size}",
				activeTags = activeTags,
				onToggle = toggleTag,
				onClear = clearTags,
				leading = if (isExpanded) {
					{ searchField() }
				} else {
					null
				},
				trailing = {
					HdSortMenu(
						label = Res.string.notes_sort_label,
						options = NotesSortMode.entries,
						selected = sortMode,
						onSelect = { sortMode = it },
					)
				},
			)

			AnimatedVisibility(visible = activeTags.isNotEmpty()) {
				HdActiveFiltersStrip(
					activeTags = activeTags,
					filteredLabel = stringResource(
						Res.string.notes_filter_filtered,
						visibleNotes.size,
						state.notes.size,
					),
					clearAllLabel = stringResource(Res.string.notes_filter_clear_all),
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
				item(span = StaggeredGridItemSpan.FullLine) {
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


@OptIn(ExperimentalSharedTransitionApi::class)
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
	with(sharedTransitionScope) {
		val date = remember(note.created) {
			note.created.toLocalDateTime(TimeZone.currentSystemDefault()).format("dd MMM `yy")
		}
		val words = remember(note.content) { note.wordCount() }

		HdMarkdownCard(
			markdown = note.content,
			metaStart = date,
			metaEnd = stringResource(Res.string.notes_word_count_short, words),
			onClick = onClick,
			modifier = modifier,
			surfaceModifier = Modifier
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "note-card-${note.id}"),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.testTag(noteCardTag(note.id)),
			metaStartModifier = Modifier.sharedElement(
				sharedContentState = rememberSharedContentState(key = "note-date-${note.id}"),
				animatedVisibilityScope = animatedVisibilityScope,
			),
			markdownModifier = Modifier.sharedElement(
				sharedContentState = rememberSharedContentState(key = "note-content-${note.id}"),
				animatedVisibilityScope = animatedVisibilityScope,
			),
			tags = note.tags,
			activeTags = activeTags,
			onTagClick = onTagClick,
		)
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
		modifier = modifier.testTag(NOTES_CREATE_FAB_TAG),
	)
}
