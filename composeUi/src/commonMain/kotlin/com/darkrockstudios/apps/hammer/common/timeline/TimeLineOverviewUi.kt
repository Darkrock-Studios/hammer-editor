package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.SelectionSummary
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdClearGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCollapsingStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDragHandle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdGlyphColor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdSwatchColor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.rememberMarkdownPreview
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.search.markdownContains
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.common.timeline.drawing.timelineOpensOnDoubleClick
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.timeline_entry_subtitle
import com.darkrockstudios.apps.hammer.timeline_filter_all
import com.darkrockstudios.apps.hammer.timeline_filter_clear_all
import com.darkrockstudios.apps.hammer.timeline_filter_filtered
import com.darkrockstudios.apps.hammer.timeline_frame_back_to_everything
import com.darkrockstudios.apps.hammer.timeline_frame_band_description
import com.darkrockstudios.apps.hammer.timeline_frame_everything
import com.darkrockstudios.apps.hammer.timeline_frame_story
import com.darkrockstudios.apps.hammer.timeline_header
import com.darkrockstudios.apps.hammer.timeline_header_meta
import com.darkrockstudios.apps.hammer.timeline_lane_filter_description
import com.darkrockstudios.apps.hammer.timeline_no_events
import com.darkrockstudios.apps.hammer.timeline_search_button
import com.darkrockstudios.apps.hammer.timeline_search_clear
import com.darkrockstudios.apps.hammer.timeline_search_close
import com.darkrockstudios.apps.hammer.timeline_search_placeholder
import com.darkrockstudios.apps.hammer.timeline_selection_clear
import com.darkrockstudios.apps.hammer.timeline_selection_more
import com.darkrockstudios.apps.hammer.timeline_selection_open
import com.darkrockstudios.apps.hammer.timeline_subject_missing
import com.darkrockstudios.apps.hammer.timeline_undated_detail
import com.darkrockstudios.apps.hammer.timeline_undated_open
import com.darkrockstudios.apps.hammer.timeline_undated_title
import com.darkrockstudios.apps.hammer.timeline_view_sequence
import com.darkrockstudios.apps.hammer.timeline_view_to_scale
import com.darkrockstudios.apps.hammer.timeline_view_undated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource

/** An event matches on its body read as prose, or on its plain-text date. */
internal fun eventMatchesQuery(event: TimeLineEvent, query: String): Boolean =
	markdownContains(event.content, query) ||
		event.label?.contains(query, ignoreCase = true) == true

/** A row also matches on the title it shows, which for a placement is its subject's name. */
internal fun rowMatchesQuery(row: TimelineRow, query: String): Boolean =
	eventMatchesQuery(row.event, query) || row.title.contains(query, ignoreCase = true)

const val TIME_LINE_CREATE_TAG = "Timeline Overview Create"
const val TIME_LINE_LIST_TAG = "Timeline Overview List"
const val TIME_LINE_SEARCH_TAG = "Timeline Overview Search"
const val TIME_LINE_LANE_FILTER_TAG = "Timeline Lane Filter"
const val TIME_LINE_CALENDAR_TAG = "Timeline Calendar"
const val TIME_LINE_UNDATED_TAG = "Timeline Undated Notice"
const val TIME_LINE_VIEW_PICKER_TAG = "Timeline View Picker"
const val TIME_LINE_FRAME_PICKER_TAG = "Timeline Frame Picker"
const val TIME_LINE_FRAME_BAND_TAG = "Timeline Frame Band"
const val TIME_LINE_FRAME_CLEAR_TAG = "Timeline Frame Clear"
const val TIME_LINE_SELECTION_TAG = "Timeline Selection Summary"
const val EVENT_CARD_TAG = "Timeline Event Card"
const val EVENT_CARD_DATE_TAG = "Timeline Event Card Date"
const val EVENT_CARD_CONTENT_TAG = "Timeline Event Card Content"
const val EVENT_CARD_TITLE_TAG = "Timeline Event Card Title"
const val EVENT_CARD_KIND_TAG = "Timeline Event Card Kind"
const val EVENT_CARD_LANE_TAG = "Timeline Event Card Lane"
const val EVENT_CARD_MILESTONE_TAG = "Timeline Event Card Milestone"
const val EVENT_CARD_MAX_CONTENT_LENGTH = 256

private val LaneStripeWidth = 4.dp

@OptIn(
	ExperimentalSharedTransitionApi::class,
	ExperimentalMaterial3Api::class,
)
@Composable
fun TimeLineOverviewUi(
	component: TimeLineOverview,
	scope: CoroutineScope,
	showCreate: () -> Unit,
	viewEvent: (eventId: Int) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()
	val tagIndex by component.rankedTags.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isWide = screen.isWide
	val isExpanded = screen.windowWidthClass == WindowWidthSizeClass.Expanded
	val phone = screen.windowWidthClass == WindowWidthSizeClass.Compact

	var searchQuery by rememberSaveable { mutableStateOf("") }
	var activeTags by remember { mutableStateOf<Set<String>>(emptySet()) }
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val rows = state.rows.orEmpty()
	val laneFilter = state.laneFilter
	val frameScope = rememberCoroutineScope()
	var pendingFrame by remember { mutableStateOf<Job?>(null) }
	val doubleClickTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis

	// Search and tags; the drawing applies the lane filter itself, keeping scenes in its ledger.
	val matchedRows by remember(rows, searchQuery, activeTags) {
		derivedStateOf {
			val byText = if (searchQuery.isBlank()) {
				rows
			} else {
				val query = searchQuery.trim()
				rows.filter { rowMatchesQuery(it, query) }
			}
			if (activeTags.isEmpty()) {
				byText
			} else {
				byText.filter { row -> activeTags.all { it in row.event.tags } }
			}
		}
	}
	val visibleRows by remember(matchedRows, laneFilter) {
		derivedStateOf {
			if (laneFilter.isEmpty()) matchedRows else matchedRows.filter { it.event.lane in laneFilter }
		}
	}
	val dated = state.mode == TimelineMode.DATED

	val toggleTag: (String) -> Unit = { tag ->
		activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag
	}
	val clearFilters: () -> Unit = {
		activeTags = emptySet()
		component.setLaneFilter(emptySet())
	}
	val anyFilter = activeTags.isNotEmpty() || laneFilter.isNotEmpty()

	val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
		state = rememberTopAppBarState(),
	)

	Column(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(scrollBehavior.nestedScrollConnection),
	) {
		// Title row — section header with entries/tags meta. Narrow widths
		// swap the title for a search field on tap.
		AnimatedContent(
			targetState = showSearchBar && !isWide,
			modifier = Modifier
				.fillMaxWidth()
				.height(Ui.TOP_BAR_HEIGHT)
				.padding(horizontal = Ui.Padding.XL),
			transitionSpec = {
				fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
			},
			label = "TimeLineTitleAnim",
		) { searching ->
			if (searching) {
				HdSearchRow(
					query = searchQuery,
					onQueryChange = { searchQuery = it },
					placeholder = Res.string.timeline_search_placeholder.get(),
					clearContentDescription = Res.string.timeline_search_clear.get(),
					onCollapse = {
						showSearchBar = false
						searchQuery = ""
					},
					collapseContentDescription = Res.string.timeline_search_close.get(),
					modifier = Modifier.fillMaxSize(),
					testTag = TIME_LINE_SEARCH_TAG,
				)
			} else {
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdSectionHeader(
						section = 4,
						title = Res.string.timeline_header.get(),
						modifier = Modifier.weight(1f),
						trailing = {
							if (isWide) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
								) {
									HdMonoLabel(
										text = Res.string.timeline_header_meta.get(
											rows.size,
											tagIndex.size,
										),
									)
									if (state.mode == TimelineMode.DATED) {
										HdMonoLabel(
											text = (state.calendar ?: CalendarDef()).displayName(),
											modifier = Modifier.testTag(TIME_LINE_CALENDAR_TAG),
										)
									}
								}
							}
						},
					)
					if (!isWide) {
						IconButton(onClick = { showSearchBar = true }) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = Res.string.timeline_search_button.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						}
					}
				}
			}
		}

		HdFolioDivider()

		HdCollapsingStrip(scrollBehavior = scrollBehavior) {
			val searchField: @Composable () -> Unit = {
				HdSearchField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					modifier = Modifier
						.then(if (isExpanded) Modifier.width(280.dp) else Modifier.fillMaxWidth()),
					placeholder = Res.string.timeline_search_placeholder.get(),
					onClear = { searchQuery = "" },
					clearContentDescription =
						Res.string.timeline_search_clear.get(),
				)
			}

			val pickers: @Composable () -> Unit = {
				if (dated) {
					DrawingPickers(
						state = state,
						rows = rows,
						isWide = isWide,
						onView = component::setView,
						onFrame = component::setFrame,
					)
				}
			}
			// A phone filters first and picks the drawing under its chips.
			if (!phone) pickers()

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

			val laneChips: (@Composable () -> Unit)? = if (state.lanes.isEmpty()) {
				null
			} else {
				{
					LaneFilterChips(
						lanes = state.lanes.map { it.lane to it.count },
						active = laneFilter,
						onToggle = component::toggleLane,
					)
				}
			}
			val leading: (@Composable () -> Unit)? = when {
				isExpanded && laneChips != null -> {
					{
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
						) {
							searchField()
							Box(
								modifier = Modifier
									.height(20.dp)
									.width(Dp.Hairline)
									.background(MaterialTheme.colorScheme.outlineVariant),
							)
							laneChips()
						}
					}
				}

				isExpanded -> {
					{ searchField() }
				}

				isWide -> laneChips
				else -> null
			}

			HdTagFilterBar(
				tags = tagIndex,
				allLabel = "${Res.string.timeline_filter_all.get()} · ${rows.size}",
				activeTags = activeTags,
				onToggle = toggleTag,
				onClear = clearFilters,
				leading = leading,
				scrollingLeading = if (isWide) null else laneChips,
			)

			AnimatedVisibility(visible = anyFilter) {
				HdActiveFiltersStrip(
					activeTags = activeTags,
					filteredLabel = Res.string.timeline_filter_filtered.get(visibleRows.size, rows.size),
					clearAllLabel = Res.string.timeline_filter_clear_all.get(),
					onToggle = toggleTag,
					onClear = clearFilters,
				)
			}

			if (phone) pickers()

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}

		val firstUndated = state.undatedIds.firstOrNull()
		if (dated && firstUndated != null) {
			HdNoticeStrip(
				title = pluralStringResource(
					Res.plurals.timeline_undated_title,
					state.undatedIds.size,
					state.undatedIds.size,
				),
				detail = Res.string.timeline_undated_detail.get(),
				primaryLabel = Res.string.timeline_undated_open.get(),
				onPrimary = { viewEvent(firstUndated) },
				modifier = Modifier.testTag(TIME_LINE_UNDATED_TAG),
			)
		}

		val listContentPadding = PaddingValues(
			horizontal = Ui.Padding.XL,
			vertical = Ui.Padding.L,
		)

		val eventRow: @Composable (row: TimelineRow, draggable: Boolean) -> Unit =
			{ row, draggable ->
				val isLast = visibleRows.lastOrNull()?.event?.id == row.event.id
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.widthIn(max = 980.dp),
				) {
					EventCard(
						row = row,
						isLast = isLast,
						activeTags = activeTags,
						onTagClick = toggleTag,
						onClick = { viewEvent(row.event.id) },
						isDraggable = draggable,
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
					)
				}
			}

		when {
			dated && matchedRows.isNotEmpty() -> {
				val selected = state.selectedId?.let { id -> rows.firstOrNull { it.event.id == id } }
				var stripHeight by remember { mutableStateOf(0.dp) }
				val density = LocalDensity.current
				Box(modifier = Modifier.fillMaxSize()) {
					DatedTimelinePane(
						state = state,
						rows = matchedRows,
						onTap = { target ->
							when (target) {
								is HitTarget.Band -> {
									if (state.selectedId != target.eventId) component.select(target.eventId)
									// Framing moves the band, so it waits until a second click could no longer open it.
									pendingFrame?.cancel()
									pendingFrame = frameScope.launch {
										if (timelineOpensOnDoubleClick) delay(doubleClickTimeout)
										component.setFrame(TimelineFrame.Band(target.eventId))
									}
								}

								is HitTarget.Point -> component.select(target.eventId)
								is HitTarget.Ledger -> component.select(target.eventId)
								is HitTarget.LaneHeader -> component.toggleLane(target.lane)
							}
						},
						onOpen = { eventId ->
							pendingFrame?.cancel()
							viewEvent(eventId)
						},
						bottomPadding = if (selected != null) stripHeight else 0.dp,
					)
					if (selected != null) {
						// The fill runs under the system navigation; the strip's content stays above it.
						Box(
							modifier = Modifier
								.align(Alignment.BottomCenter)
								.fillMaxWidth()
								.onSizeChanged { stripHeight = with(density) { it.height.toDp() } }
								.background(MaterialTheme.colorScheme.surfaceContainerLow)
								.navigationBarsPadding(),
						) {
							SelectionStrip(
								row = selected,
								summary = state.selectionSummary?.takeIf { it.row.event.id == selected.event.id },
								onOpen = { viewEvent(selected.event.id) },
								onClear = { component.select(null) },
								onReference = component::openReference,
								maxChips = if (phone) SUMMARY_CHIPS_PHONE else SUMMARY_CHIPS,
							)
						}
					}
				}
			}

			visibleRows.isEmpty() -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.testTag(TIME_LINE_LIST_TAG)
						.padding(vertical = Ui.Padding.XXL),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = Res.string.timeline_no_events.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
					)
				}
			}
			// Reordering maps display indices to stored order, which only holds when nothing is
			// filtered out and the order is manual. Other views fall back to a static list.
			state.mode == TimelineMode.MANUAL && searchQuery.isBlank() && !anyFilter -> {
				DragDropList(
					items = visibleRows,
					key = { _, item -> item.event.id },
					onMove = { from, to ->
						visibleRows.getOrNull(from)?.let { row ->
							scope.launch { component.moveEvent(row.event, to, from < to) }
						}
					},
					modifier = Modifier
						.fillMaxSize()
						.testTag(TIME_LINE_LIST_TAG),
					contentPadding = listContentPadding,
				) { row, _ ->
					eventRow(row, true)
				}
			}

			else -> {
				val listState = rememberLazyListState()
				Box {
					LazyColumn(
						state = listState,
						modifier = Modifier
							.fillMaxSize()
							.testTag(TIME_LINE_LIST_TAG),
						contentPadding = listContentPadding,
					) {
						items(
							items = visibleRows,
							key = { row -> row.event.id },
						) { row ->
							eventRow(row, false)
						}
					}

					MpScrollBarList(
						modifier = scrollBarOverlay(),
						state = listState,
					)
				}
			}
		}
	}
}

/** The view picker and the frame picker, side by side when there is room. */
@Composable
private fun DrawingPickers(
	state: TimeLineOverview.State,
	rows: List<TimelineRow>,
	isWide: Boolean,
	onView: (TimelineView) -> Unit,
	onFrame: (TimelineFrame) -> Unit,
) {
	val viewPicker: @Composable (Modifier) -> Unit = { modifier ->
		HdHairlineSegmentedPicker(
			options = TimelineView.entries,
			selected = state.view,
			onSelect = onView,
			label = { view ->
				when (view) {
					TimelineView.SEQUENCE -> Res.string.timeline_view_sequence.get()
					TimelineView.TO_SCALE -> Res.string.timeline_view_to_scale.get()
				}
			},
			modifier = modifier.testTag(TIME_LINE_VIEW_PICKER_TAG),
		)
	}
	val band = state.frame as? TimelineFrame.Band
	val bandTitle = band?.let { frame -> rows.firstOrNull { it.event.id == frame.eventId }?.title }.orEmpty()
	val framePicker: @Composable (Modifier) -> Unit = { modifier ->
		FramePicker(
			frame = state.frame,
			storyAvailable = state.storyFrameAvailable,
			bandTitle = bandTitle,
			onSelect = onFrame,
			modifier = modifier,
		)
	}
	if (isWide) {
		Row(
			modifier = Modifier.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
		) {
			viewPicker(Modifier.width(280.dp))
			framePicker(Modifier.width(if (band != null) 420.dp else 280.dp))
		}
	} else {
		Column(
			modifier = Modifier.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			viewPicker(Modifier.fillMaxWidth())
			framePicker(Modifier.fillMaxWidth())
		}
	}
}

/** `[ EVERYTHING │ STORY ]`, plus the framed band's cell with a clear glyph while one is framed. */
@Composable
private fun FramePicker(
	frame: TimelineFrame,
	storyAvailable: Boolean,
	bandTitle: String,
	onSelect: (TimelineFrame) -> Unit,
	modifier: Modifier = Modifier,
) {
	val options = buildList {
		add(TimelineFrame.Everything)
		add(TimelineFrame.Story)
		if (frame is TimelineFrame.Band) add(frame)
	}
	val bandDescription = Res.string.timeline_frame_band_description.get(bandTitle)
	val backDescription = Res.string.timeline_frame_back_to_everything.get()
	HdHairlineSegmentedPicker(
		options = options,
		selected = frame,
		onSelect = onSelect,
		label = { option ->
			when (option) {
				TimelineFrame.Everything -> Res.string.timeline_frame_everything.get()
				TimelineFrame.Story -> Res.string.timeline_frame_story.get()
				is TimelineFrame.Band -> bandTitle.ellipsize(BAND_CELL_CHARS)
			}
		},
		isEnabled = { option -> option != TimelineFrame.Story || storyAvailable },
		trailing = { option ->
			if (option is TimelineFrame.Band) {
				{
					HdClearGlyph(
						onClick = { onSelect(TimelineFrame.Everything) },
						contentDescription = backDescription,
						modifier = Modifier.testTag(TIME_LINE_FRAME_CLEAR_TAG),
					)
				}
			} else {
				null
			}
		},
		cellModifier = { option ->
			if (option is TimelineFrame.Band) {
				Modifier
					.testTag(TIME_LINE_FRAME_BAND_TAG)
					.semantics { contentDescription = bandDescription }
			} else {
				Modifier
			}
		},
		modifier = modifier.testTag(TIME_LINE_FRAME_PICKER_TAG),
	)
}

private const val BAND_CELL_CHARS = 18
private const val SUMMARY_CHIPS = 6
private const val SUMMARY_CHIPS_PHONE = 4
private const val SUMMARY_CHIP_LINES = 2
private const val SUMMARY_CHIP_NAME_CHARS = 12

private fun String.ellipsize(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

/**
 * The selected event over the bottom of the drawing: its title, when and lane, then up to [maxChips]
 * of its references (or a placed entry's opening line), with Open and Clear. A reference chip opens
 * that reference through [onReference].
 */
@Composable
internal fun SelectionStrip(
	row: TimelineRow,
	summary: SelectionSummary?,
	onOpen: () -> Unit,
	onClear: () -> Unit,
	onReference: (CatalogItem) -> Unit,
	modifier: Modifier = Modifier,
	maxChips: Int = SUMMARY_CHIPS,
) {
	val words = rememberStoryDateWords()
	val start = row.start
	val whenText = if (start != null) {
		formatStoryDateRange(row.calendar ?: CalendarDef(), start, row.end, words)
	} else {
		row.whenText.ifBlank { null }
	}
	val lane = row.event.lane?.takeIf { it.isNotBlank() }
	val references = summary?.references.orEmpty()
	val excerpt = summary?.excerpt
	HdNoticeStrip(
		title = row.title,
		detail = listOfNotNull(whenText, lane).joinToString(" · ").ifBlank { null },
		primaryLabel = Res.string.timeline_selection_open.get(),
		onPrimary = onOpen,
		secondaryLabel = Res.string.timeline_selection_clear.get(),
		onSecondary = onClear,
		modifier = modifier.testTag(TIME_LINE_SELECTION_TAG),
		content = when {
			references.isNotEmpty() -> {
				{ SummaryChips(references = references, max = maxChips, onClick = onReference) }
			}

			excerpt != null -> {
				{
					Text(
						text = excerpt,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			else -> null
		},
	)
}

/**
 * Up to [max] read-only reference chips, glyph and name, on at most [SUMMARY_CHIP_LINES] lines,
 * then `+N` for every reference not shown, whether past [max] or past the last line.
 */
@Composable
private fun SummaryChips(references: List<CatalogItem>, max: Int, onClick: (CatalogItem) -> Unit) {
	val shown = references.take(max)
	SubcomposeLayout(modifier = Modifier.padding(top = 4.dp)) { constraints ->
		val gap = 6.dp.roundToPx()
		val loose = constraints.copy(minWidth = 0, minHeight = 0)
		val chips = subcompose("chips") {
			shown.forEach { item ->
				HdReferenceChip(
					glyph = item.hdMark(),
					swatchColor = item.hdSwatchColor(),
					glyphColor = item.hdGlyphColor(),
					name = item.name.ellipsize(SUMMARY_CHIP_NAME_CHARS),
					onClick = { onClick(item) },
					onAction = null,
					actionContentDescription = "",
				)
			}
		}.map { it.measure(loose) }

		// The most chips that fit with the `+N` for the rest after them.
		var lines: List<List<Placeable>>? = null
		var count = chips.size
		while (lines == null) {
			val hidden = references.size - count
			val more = if (hidden > 0) {
				subcompose("more-$hidden") { SummaryMore(hidden) }.single().measure(loose)
			} else {
				null
			}
			lines = wrapLines(chips.take(count) + listOfNotNull(more), constraints.maxWidth, gap)
				.takeIf { it.size <= SUMMARY_CHIP_LINES || count == 0 }
			count--
		}

		val heights = lines.map { line -> line.maxOf { it.height } }
		val height = heights.sum() + gap * (lines.size - 1).coerceAtLeast(0)
		val width = lines.maxOfOrNull { line -> line.sumOf { it.width } + gap * (line.size - 1) } ?: 0
		layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceAtLeast(constraints.minHeight)) {
			var y = 0
			lines.forEachIndexed { index, line ->
				var x = 0
				line.forEach { placeable ->
					placeable.placeRelative(x, y + (heights[index] - placeable.height) / 2)
					x += placeable.width + gap
				}
				y += heights[index] + gap
			}
		}
	}
}

/** [items] wrapped into lines no wider than [maxWidth], one item a line where an item alone is wider. */
private fun wrapLines(items: List<Placeable>, maxWidth: Int, gap: Int): List<List<Placeable>> {
	val lines = mutableListOf<MutableList<Placeable>>()
	var x = 0
	items.forEach { item ->
		val line = lines.lastOrNull()
		if (line != null && x + gap + item.width <= maxWidth) {
			line += item
			x += gap + item.width
		} else {
			lines += mutableListOf(item)
			x = item.width
		}
	}
	return lines
}

@Composable
private fun SummaryMore(count: Int) {
	HdMonoLabel(text = Res.string.timeline_selection_more.get(count))
}

/** One `HdTagChip` per lane, its swatch in the lane's colour: `▪ Alice · 4`. */
@Composable
private fun LaneFilterChips(
	lanes: List<Pair<String, Int>>,
	active: Set<String>,
	onToggle: (String) -> Unit,
) {
	val colors = LocalHammerColors.current
	Row(
		modifier = Modifier.testTag(TIME_LINE_LANE_FILTER_TAG),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		lanes.forEachIndexed { index, (lane, count) ->
			val description = Res.string.timeline_lane_filter_description.get(lane)
			HdTagChip(
				label = "$lane · $count",
				active = lane in active,
				accent = colors.laneColor(index),
				onClick = { onToggle(lane) },
				modifier = Modifier.semantics { contentDescription = description },
			)
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventCard(
	row: TimelineRow,
	isLast: Boolean,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	onClick: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	isDraggable: Boolean = false,
) {
	val event = row.event
	val railColor = MaterialTheme.colorScheme.outlineVariant
	val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
	val surfaceColor = MaterialTheme.colorScheme.surface
	val railColumnWidth = 40.dp
	val railGutter = Ui.Padding.L
	val laneIndex = row.laneIndex

	// Rail is drawn behind the row's full height (including the inter-card
	// gap), so the line reads as one continuous chronology instead of a
	// stack of disconnected segments. drawBehind sits before the bottom
	// padding so the rail extends through the gap; the slip's bottom
	// padding becomes the gap to the next entry's dot.
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.drawBehind {
				val railX = railColumnWidth.toPx() - 1.dp.toPx()
				val dotSize = 9.dp.toPx()
				val dotTop = 18.dp.toPx()
				val lineEnd = if (isLast) dotTop + dotSize / 2f else size.height
				drawLine(
					color = railColor,
					start = Offset(railX, 0f),
					end = Offset(railX, lineEnd),
					strokeWidth = 1.dp.toPx(),
				)
				val dotLeft = railX - dotSize / 2f
				drawRect(
					color = surfaceColor,
					topLeft = Offset(dotLeft - 1.dp.toPx(), dotTop - 1.dp.toPx()),
					size = Size(dotSize + 2.dp.toPx(), dotSize + 2.dp.toPx()),
				)
				drawRect(
					color = dotColor,
					topLeft = Offset(dotLeft, dotTop),
					size = Size(dotSize, dotSize),
				)
			}
			.padding(bottom = Ui.Padding.L),
	) {
		with(sharedTransitionScope) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = railColumnWidth + railGutter)
					.wrapContentHeight()
					.background(MaterialTheme.colorScheme.surfaceContainerLow, RectangleShape)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "timeline-card-${event.id}"),
						animatedVisibilityScope = animatedVisibilityScope,
					)
					.clickable(onClick = onClick)
					.testTag(EVENT_CARD_TAG),
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = if (laneIndex != null) LaneStripeWidth else 0.dp),
				) {
					CardHeader(
						row = row,
						isDraggable = isDraggable,
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
					)

					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)

					CardBody(
						row = row,
						activeTags = activeTags,
						onTagClick = onTagClick,
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
					)
				}

				if (laneIndex != null) {
					// Measured after the card, so it spans the card's full height.
					Box(modifier = Modifier.matchParentSize()) {
						Box(
							modifier = Modifier
								.fillMaxHeight()
								.width(LaneStripeWidth)
								.background(LocalHammerColors.current.laneColor(laneIndex))
								.testTag(EVENT_CARD_LANE_TAG),
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardHeader(
	row: TimelineRow,
	isDraggable: Boolean,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val event = row.event
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		TimelineKindMark(kind = event.kind, modifier = Modifier.testTag(EVENT_CARD_KIND_TAG))
		if (event.milestone) {
			MilestoneMark(modifier = Modifier.testTag(EVENT_CARD_MILESTONE_TAG))
		}
		val labelStyle = MaterialTheme.typography.labelSmall.let {
			if (event.milestone) it.copy(fontWeight = FontWeight.Bold) else it
		}
		val words = rememberStoryDateWords()
		val start = row.start
		val whenText = if (start != null) {
			formatStoryDateRange(row.calendar ?: CalendarDef(), start, row.end, words)
		} else {
			row.whenText.ifBlank { Res.string.timeline_view_undated.get() }
		}
		with(sharedTransitionScope) {
			HdMonoLabel(
				text = whenText,
				style = labelStyle,
				color = if (event.milestone) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				modifier = Modifier
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "timeline-date-${event.id}"),
						animatedVisibilityScope = animatedVisibilityScope,
					)
					.testTag(EVENT_CARD_DATE_TAG),
			)
		}
		Spacer(modifier = Modifier.weight(1f))
		if (isDraggable) {
			HdDragHandle()
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CardBody(
	row: TimelineRow,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val event = row.event
	val placed = event.kind.isPlacement
	// A plain event without a title is named by its first line, which the preview already shows.
	val showTitle = row.title.isNotBlank() && (placed || !event.title.isNullOrBlank())

	if (row.subjectMissing) {
		HdMonoLabel(
			text = Res.string.timeline_subject_missing.get(),
			color = LocalHammerColors.current.warning,
			modifier = Modifier.padding(start = Ui.Padding.L, end = Ui.Padding.L, top = Ui.Padding.M),
		)
	}

	if (showTitle) {
		Text(
			text = row.title,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = Ui.Padding.L, end = Ui.Padding.L, top = Ui.Padding.M)
				.testTag(EVENT_CARD_TITLE_TAG),
		)
	}

	if (placed) {
		val subtitle = when (event.kind) {
			TimelineKind.SCENE -> row.subtitle
			TimelineKind.ENTRY -> Res.string.timeline_entry_subtitle.get()
			TimelineKind.EVENT -> null
		}
		if (subtitle != null && !row.subjectMissing) {
			HdMonoLabel(
				text = subtitle,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(start = Ui.Padding.L, end = Ui.Padding.L, top = 4.dp),
			)
		}
		Spacer(modifier = Modifier.height(Ui.Padding.M))
		return
	}

	with(sharedTransitionScope) {
		val previewMarkdown = rememberMarkdownPreview(event.content, EVENT_CARD_MAX_CONTENT_LENGTH)
		MarkdownView(
			markdown = previewMarkdown,
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					horizontal = Ui.Padding.L,
					vertical = Ui.Padding.M,
				)
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "timeline-content-${event.id}"),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.testTag(EVENT_CARD_CONTENT_TAG),
		)
	}

	if (event.tags.isNotEmpty()) {
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
			event.tags.sorted().forEach { tag ->
				HdTagChip(
					label = tag,
					active = tag in activeTags,
					onClick = { onTagClick(tag) },
				)
			}
		}
	}
}
