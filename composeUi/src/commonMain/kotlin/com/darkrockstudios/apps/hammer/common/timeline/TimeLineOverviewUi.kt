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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.compose.LocalRootFabState
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdScrollAwayStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayFooterState
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayStripState
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.truncateWithEllipsis
import com.darkrockstudios.apps.hammer.common.timeline.drawing.eventId
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.timeline_filter_all
import com.darkrockstudios.apps.hammer.timeline_filter_clear_all
import com.darkrockstudios.apps.hammer.timeline_filter_filtered
import com.darkrockstudios.apps.hammer.timeline_frame_story
import com.darkrockstudios.apps.hammer.timeline_header_meta
import com.darkrockstudios.apps.hammer.timeline_no_events
import com.darkrockstudios.apps.hammer.timeline_search_button
import com.darkrockstudios.apps.hammer.timeline_search_clear
import com.darkrockstudios.apps.hammer.timeline_search_close
import com.darkrockstudios.apps.hammer.timeline_search_matches
import com.darkrockstudios.apps.hammer.timeline_search_placeholder
import com.darkrockstudios.apps.hammer.timeline_undated_detail
import com.darkrockstudios.apps.hammer.timeline_undated_open
import com.darkrockstudios.apps.hammer.timeline_undated_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.math.roundToInt

const val TIME_LINE_CREATE_TAG = "Timeline Overview Create"
const val TIME_LINE_LIST_TAG = "Timeline Overview List"
const val TIME_LINE_SEARCH_TAG = "Timeline Overview Search"
const val TIME_LINE_LANE_FILTER_TAG = "Timeline Lane Filter"
const val TIME_LINE_CALENDAR_TAG = "Timeline Calendar"
const val TIME_LINE_UNDATED_TAG = "Timeline Undated Notice"
const val TIME_LINE_DRAWING_MENU_TAG = "Timeline Drawing Menu"
const val TIME_LINE_SEARCH_MATCHES_TAG = "Timeline Search Matches"
const val TIME_LINE_FRAME_PILL_TAG = "Timeline Frame Pill"

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
	switcher: TimelineSwitcherActions,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	openEvent: (eventId: Int) -> Unit = viewEvent,
	openedEventId: Int? = null,
	lastLayout: LastTimelineLayout = remember { LastTimelineLayout() },
	onOrphansLeft: (left: Int) -> Unit = {},
) {
	val state by component.state.subscribeAsState()
	val tagIndex by component.rankedTags.subscribeAsState()
	val previews by component.previews.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isWide = screen.isWide
	val isExpanded = screen.windowWidthClass == WindowWidthSizeClass.Expanded

	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val rows = state.rows.orEmpty()
	val searchQuery = state.searchQuery
	val activeTags = state.tagFilter
	val laneFilter = state.laneFilter
	val visibleRows = state.filtered.listRows
	val searchMatches = state.filtered.searchMatches

	val dated = state.mode == TimelineMode.DATED
	val highlighted = remember(searchMatches) { searchMatches.toSet() }
	var matchIndex by remember(searchMatches) { mutableIntStateOf(0) }
	val focusedMatch = searchMatches.getOrNull(matchIndex)
	// On the drawing Enter steps through the matches, counted beside the field.
	val nextMatch: ((String) -> Unit)? = if (dated) {
		{ if (searchMatches.isNotEmpty()) matchIndex = (matchIndex + 1) % searchMatches.size }
	} else {
		null
	}
	val matchCounter = if (dated && searchQuery.isNotBlank()) {
		Res.string.timeline_search_matches.get(if (searchMatches.isEmpty()) 0 else matchIndex + 1, searchMatches.size)
	} else {
		null
	}
	val toggleTag: (String) -> Unit = component::toggleTag
	val clearFilters: () -> Unit = component::clearFilters
	val anyFilter = activeTags.isNotEmpty() || laneFilter.isNotEmpty()
	val bandTitle = (state.frame as? TimelineFrame.Band)
		?.let { frame -> rows.firstOrNull { it.event.id == frame.eventId }?.title }
	val words = rememberStoryDateWords()

	val branch = when {
		dated && state.filtered.drawingRows.isNotEmpty() -> OverviewContent.Drawing
		visibleRows.isEmpty() -> OverviewContent.Empty
		else -> OverviewContent.List
	}
	val listState = rememberLazyListState()
	val drawingScroll = rememberScrollState()
	val scrollStrip = rememberHdScrollAwayStripState()
	// Content that cannot scroll has no scroll position to keep the strip or the create button hidden.
	// One derived state follows the branch, since the effect below holds on to it as the timeline loads.
	val currentBranch by rememberUpdatedState(branch)
	val contentFits by remember {
		derivedStateOf {
			when (currentBranch) {
				OverviewContent.Drawing -> drawingScroll.maxValue == 0
				OverviewContent.Empty -> true
				OverviewContent.List -> !listState.canScrollForward && !listState.canScrollBackward
			}
		}
	}
	LaunchedEffect(branch, scrollStrip) {
		snapshotFlow {
			when (branch) {
				OverviewContent.Drawing -> drawingScroll.value
				OverviewContent.Empty -> 0
				OverviewContent.List -> if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else Int.MAX_VALUE
			}
		}.collect(scrollStrip::followScrollPosition)
	}
	val scrollAway = rememberHdScrollAwayFooterState()
	val rootFab = LocalRootFabState.current
	LaunchedEffect(rootFab, scrollAway) {
		rootFab ?: return@LaunchedEffect
		snapshotFlow { scrollAway.isHiddenByScroll && !contentFits }.collect { rootFab.scrolledDown = it }
	}
	DisposableEffect(rootFab) {
		onDispose { rootFab?.scrolledDown = false }
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(scrollAway.nestedScrollConnection),
	) {
		// Title row — section header with entries/tags meta. Narrow widths
		// swap the title for a search field on tap.
		AnimatedContent(
			// A kept search shows its field, so what narrows the results can be seen and cleared.
			targetState = (showSearchBar || searchQuery.isNotBlank()) && !isWide,
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
					onQueryChange = component::setSearchQuery,
					placeholder = Res.string.timeline_search_placeholder.get(),
					clearContentDescription = Res.string.timeline_search_clear.get(),
					onCollapse = {
						showSearchBar = false
						component.setSearchQuery("")
					},
					collapseContentDescription = Res.string.timeline_search_close.get(),
					modifier = Modifier.fillMaxSize(),
					testTag = TIME_LINE_SEARCH_TAG,
					onSearch = nextMatch,
					status = matchCounter,
					statusTestTag = TIME_LINE_SEARCH_MATCHES_TAG,
				)
			} else {
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
				) {
					TimelineSwitcherHeader(
						state = state,
						actions = switcher,
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
									if (dated) {
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
						if (dated) {
							DrawingMenu(
								state = state,
								bandTitle = bandTitle,
								onView = component::setView,
								onFrame = component::setFrame,
								compact = true,
							)
						}
					}
				}
			}
		}

		HdFolioDivider()

		HdScrollAwayStrip(
			state = scrollStrip,
			modifier = Modifier.weight(1f),
			strip = {
				val searchField: @Composable () -> Unit = {
					Row(
						modifier = if (isExpanded) Modifier else Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						HdSearchField(
							value = searchQuery,
							onValueChange = component::setSearchQuery,
							modifier = if (isExpanded) Modifier.width(280.dp) else Modifier.weight(1f),
							placeholder = Res.string.timeline_search_placeholder.get(),
							onSearch = nextMatch,
							onClear = { component.setSearchQuery("") },
							clearContentDescription =
								Res.string.timeline_search_clear.get(),
						)
						if (matchCounter != null) {
							HdMonoLabel(text = matchCounter, modifier = Modifier.testTag(TIME_LINE_SEARCH_MATCHES_TAG))
						}
					}
				}

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
				val leading: (@Composable () -> Unit)? = if (isExpanded) {
					{ searchField() }
				} else {
					null
				}

				HdTagFilterBar(
					tags = tagIndex,
					allLabel = "${Res.string.timeline_filter_all.get()} · ${rows.size}",
					activeTags = activeTags,
					onToggle = toggleTag,
					onClear = clearFilters,
					leading = leading,
					scrollingLeading = laneChips,
					trailing = {
						if (dated && isWide) {
							DrawingMenu(
								state = state,
								bandTitle = bandTitle,
								onView = component::setView,
								onFrame = component::setFrame,
							)
						}
					},
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

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				if (state.isOrphans) {
					DeletedTimelineNotice(
						state = state,
						onMoveAll = { target ->
							val before = state.orphanCount
							scope.launch {
								val moved = component.moveOrphansTo(target)
								if (moved < before) onOrphansLeft(before - moved)
							}
						},
						onDeleteAll = { scope.launch { component.deleteOrphans() } },
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
						onPrimary = { openEvent(firstUndated) },
						modifier = Modifier.testTag(TIME_LINE_UNDATED_TAG),
					)
				}
			},
		) { topPadding ->
			val listContentPadding = PaddingValues(
				start = Ui.Padding.XL,
				top = Ui.Padding.L + topPadding,
				end = Ui.Padding.XL,
				bottom = Ui.Padding.L,
			)

			// Judged on every row, not the filtered ones, so the list does not shift as filters change.
			val whenColumn = isWide && (dated || rows.any { it.whenText.isNotBlank() })
			val eventRow: @Composable (row: TimelineRow, draggable: Boolean) -> Unit =
				{ row, draggable ->
					val placed = row.event.kind.isPlacement
					if (placed) {
						LaunchedEffect(row) { component.requestPreview(row.event.id) }
					}
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.widthIn(max = 980.dp),
					) {
						TimelineRailRow(
							row = row,
							laneCount = state.lanes.size,
							whenColumn = whenColumn,
							onClick = { viewEvent(row.event.id) },
							dated = dated,
							isDraggable = draggable,
							placementExcerpt = if (placed) previews[row.event.id]?.let { it.excerpt.orEmpty() } else null,
							words = words,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
					}
				}

			when {
				branch == OverviewContent.Drawing -> {
					val framed = state.frame != TimelineFrame.Everything
					Box(modifier = Modifier.fillMaxSize()) {
						DatedTimelinePane(
							state = state,
							rows = state.filtered.drawingRows,
							onTap = { target ->
								when (target) {
									is HitTarget.LaneHeader -> component.toggleLane(target.lane)
									else -> target.eventId?.let(openEvent)
								}
							},
							// The event's mark hands its bounds to the dialog opened from it, and takes them back.
							// Its own visibility transition is what drives the shrink back into it on close.
							eventSource = { id ->
								EventMarkSource(
									eventId = id,
									visible = openedEventId != id,
									sharedTransitionScope = sharedTransitionScope,
									modifier = Modifier.matchParentSize(),
								)
							},
							previews = component.previews,
							requestPreview = component::requestPreview,
							scrollState = drawingScroll,
							topPadding = topPadding,
							highlighted = highlighted,
							focusedId = focusedMatch,
							lastLayout = lastLayout,
						)
						if (framed) {
							FramePill(
								label = bandTitle?.truncateWithEllipsis(BAND_CELL_CHARS) ?: Res.string.timeline_frame_story.get(),
								onClear = { component.setFrame(TimelineFrame.Everything) },
								modifier = Modifier
									.align(Alignment.TopEnd)
									// Rides just under whatever part of the strip is showing.
									.offset { IntOffset(0, scrollStrip.visibleHeight.roundToInt()) }
									.padding(top = Ui.Padding.M, end = Ui.Padding.L + MpScrollBarGutter),
							)
						}
					}
				}

				branch == OverviewContent.Empty -> {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.testTag(TIME_LINE_LIST_TAG)
							.padding(top = topPadding)
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
				state.filtered.reorderable -> {
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
						lazyListState = listState,
					) { row, _ ->
						eventRow(row, true)
					}
				}

				else -> {
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
}

private enum class OverviewContent { Drawing, Empty, List }
