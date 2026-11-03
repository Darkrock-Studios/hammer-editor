package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.basicPreview
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdClearGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdScrollAwayStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDragHandle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayFooterState
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayStripState
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
import com.darkrockstudios.apps.hammer.timeline_subject_archived
import com.darkrockstudios.apps.hammer.timeline_subject_missing
import com.darkrockstudios.apps.hammer.timeline_undated_detail
import com.darkrockstudios.apps.hammer.timeline_undated_open
import com.darkrockstudios.apps.hammer.timeline_undated_title
import com.darkrockstudios.apps.hammer.timeline_view_sequence
import com.darkrockstudios.apps.hammer.timeline_view_to_scale
import com.darkrockstudios.apps.hammer.timeline_view_undated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import com.darkrockstudios.apps.hammer.timeline_drawing_menu_frame
import com.darkrockstudios.apps.hammer.timeline_drawing_menu_view
import com.darkrockstudios.apps.hammer.timeline_drawing_menu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortPill
import androidx.compose.runtime.mutableIntStateOf
import com.darkrockstudios.apps.hammer.timeline_search_matches

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
const val TIME_LINE_DRAWING_MENU_TAG = "Timeline Drawing Menu"
const val TIME_LINE_SEARCH_MATCHES_TAG = "Timeline Search Matches"
const val TIME_LINE_FRAME_PILL_TAG = "Timeline Frame Pill"
const val EVENT_CARD_TAG = "Timeline Event Card"
const val EVENT_CARD_DATE_TAG = "Timeline Event Card Date"
const val EVENT_CARD_CONTENT_TAG = "Timeline Event Card Content"
const val EVENT_CARD_TITLE_TAG = "Timeline Event Card Title"
const val EVENT_CARD_ARCHIVED_TAG = "Timeline Event Card Archived"
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
	openEvent: (eventId: Int) -> Unit = viewEvent,
	openedEventId: Int? = null,
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

	val dated = state.mode == TimelineMode.DATED
	val taggedRows by remember(rows, activeTags) {
		derivedStateOf {
			if (activeTags.isEmpty()) rows else rows.filter { row -> activeTags.all { it in row.event.tags } }
		}
	}
	// The list narrows to what the search matches. The drawing keeps every event, applies the lane
	// filter itself (keeping scenes in its ledger), and rings the matches instead.
	val matchedRows by remember(taggedRows, searchQuery, dated) {
		derivedStateOf {
			if (dated || searchQuery.isBlank()) {
				taggedRows
			} else {
				val query = searchQuery.trim()
				taggedRows.filter { rowMatchesQuery(it, query) }
			}
		}
	}
	val searchMatches by remember(taggedRows, searchQuery, dated, laneFilter) {
		derivedStateOf {
			if (!dated || searchQuery.isBlank()) {
				emptyList()
			} else {
				val query = searchQuery.trim()
				taggedRows.filter { row ->
					val drawn = row.start != null && !row.subjectArchived
					val inLane = laneFilter.isEmpty() || row.event.kind == TimelineKind.SCENE ||
						row.event.lane.isNullOrBlank() || row.event.lane in laneFilter
					drawn && inLane && rowMatchesQuery(row, query)
				}.map { it.event.id }
			}
		}
	}
	var matchIndex by remember(searchMatches) { mutableIntStateOf(0) }
	val focusedMatch = searchMatches.getOrNull(matchIndex)
	val nextMatch: () -> Unit = {
		if (searchMatches.isNotEmpty()) matchIndex = (matchIndex + 1) % searchMatches.size
	}
	val matchCounter = if (dated && searchQuery.isNotBlank()) {
		Res.string.timeline_search_matches.get(if (searchMatches.isEmpty()) 0 else matchIndex + 1, searchMatches.size)
	} else {
		null
	}
	val visibleRows by remember(matchedRows, laneFilter) {
		derivedStateOf {
			if (laneFilter.isEmpty()) matchedRows else matchedRows.filter { it.event.lane in laneFilter }
		}
	}

	val toggleTag: (String) -> Unit = { tag ->
		activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag
	}
	val clearFilters: () -> Unit = {
		activeTags = emptySet()
		component.setLaneFilter(emptySet())
	}
	val anyFilter = activeTags.isNotEmpty() || laneFilter.isNotEmpty()

	val branch = when {
		dated && matchedRows.isNotEmpty() -> OverviewContent.Drawing
		visibleRows.isEmpty() -> OverviewContent.Empty
		else -> OverviewContent.List
	}
	val listState = rememberLazyListState()
	val drawingScroll = rememberScrollState()
	val scrollStrip = rememberHdScrollAwayStripState()
	// Content that cannot scroll has no scroll position to keep the strip or the create button hidden.
	val contentFits by remember(branch) {
		derivedStateOf {
			when (branch) {
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
	LaunchedEffect(component, scrollAway) {
		snapshotFlow { scrollAway.isHiddenByScroll && !contentFits }.collect(component::setScrolledDown)
	}
	DisposableEffect(component) {
		onDispose { component.setScrolledDown(false) }
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(scrollAway.nestedScrollConnection),
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

		HdScrollAwayStrip(
			state = scrollStrip,
			modifier = Modifier.weight(1f),
			strip = {
				// On the drawing Enter steps through the matches, counted beside the field.
				val searchField: @Composable () -> Unit = {
					Row(
						modifier = if (isExpanded) Modifier else Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						HdSearchField(
							value = searchQuery,
							onValueChange = { searchQuery = it },
							modifier = if (isExpanded) Modifier.width(280.dp) else Modifier.weight(1f),
							placeholder = Res.string.timeline_search_placeholder.get(),
							onSearch = if (dated) {
								{ nextMatch() }
							} else {
								null
							},
							onClear = { searchQuery = "" },
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
					trailing = {
						if (dated) {
							DrawingMenu(
								state = state,
								rows = rows,
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
				branch == OverviewContent.Drawing -> {
					val framed = state.frame != TimelineFrame.Everything
					Box(modifier = Modifier.fillMaxSize()) {
						DatedTimelinePane(
							state = state,
							rows = matchedRows,
							onTap = { target ->
								when (target) {
									is HitTarget.Band -> openEvent(target.eventId)
									is HitTarget.Point -> openEvent(target.eventId)
									is HitTarget.Ledger -> openEvent(target.eventId)
									is HitTarget.LaneHeader -> component.toggleLane(target.lane)
								}
							},
							onPeek = component::select,
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
							preview = component::preview,
							scrollState = drawingScroll,
							topPadding = topPadding,
							highlighted = searchMatches.toSet(),
							focusedId = focusedMatch,
						)
						if (framed) {
							val bandTitle = (state.frame as? TimelineFrame.Band)
								?.let { frame -> rows.firstOrNull { it.event.id == frame.eventId }?.title }
							FramePill(
								label = bandTitle?.ellipsize(BAND_CELL_CHARS) ?: Res.string.timeline_frame_story.get(),
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

/**
 * An invisible stand-in for an event's mark that the dialog opened from it grows out of. Hidden while
 * that dialog is open; its reappearance is the transition the dialog shrinks back into.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EventMarkSource(
	eventId: Int,
	visible: Boolean,
	sharedTransitionScope: SharedTransitionScope,
	modifier: Modifier = Modifier,
) {
	AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = modifier) {
		with(sharedTransitionScope) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.sharedElement(
						sharedContentState = rememberSharedContentState(eventDialogKey(eventId)),
						animatedVisibilityScope = this@AnimatedVisibility,
					),
			)
		}
	}
}

/**
 * The frame the drawing is on, over its top corner where it never scrolls away; tapping it returns
 * to Everything.
 */
@Composable
private fun FramePill(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
	val description = Res.string.timeline_frame_back_to_everything.get()
	Row(
		modifier = modifier
			.testTag(TIME_LINE_FRAME_PILL_TAG)
			.border(Dp.Hairline, MaterialTheme.colorScheme.outline, RectangleShape)
			.background(MaterialTheme.colorScheme.surface)
			.clickable(onClick = onClear)
			.semantics(mergeDescendants = true) {
				contentDescription = description
				role = Role.Button
			}
			.padding(start = Ui.Padding.M, end = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
		HdClearGlyph(onClick = null)
	}
}

/**
 * `VIEW  SEQUENCE · EVERYTHING` at the end of the filter row, opening one menu with the drawing
 * (Sequence or To scale) above the frame (Everything or Story). A framed band shows its title as the
 * frame; the pill on the drawing returns it to Everything.
 */
@Composable
private fun DrawingMenu(
	state: TimeLineOverview.State,
	rows: List<TimelineRow>,
	onView: (TimelineView) -> Unit,
	onFrame: (TimelineFrame) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val sequence = Res.string.timeline_view_sequence.get()
	val toScale = Res.string.timeline_view_to_scale.get()
	val everything = Res.string.timeline_frame_everything.get()
	val story = Res.string.timeline_frame_story.get()
	val bandTitle = (state.frame as? TimelineFrame.Band)
		?.let { frame -> rows.firstOrNull { it.event.id == frame.eventId }?.title }
	val bandDescription = bandTitle?.let { Res.string.timeline_frame_band_description.get(it) }
	val viewLabel: (TimelineView) -> String = { view ->
		when (view) {
			TimelineView.SEQUENCE -> sequence
			TimelineView.TO_SCALE -> toScale
		}
	}
	val frameLabel: (TimelineFrame) -> String = { frame ->
		when (frame) {
			TimelineFrame.Everything -> everything
			TimelineFrame.Story -> story
			is TimelineFrame.Band -> bandTitle.orEmpty().ellipsize(BAND_CELL_CHARS)
		}
	}
	Box {
		HdSortPill(
			label = Res.string.timeline_drawing_menu.get(),
			value = "${viewLabel(state.view)} · ${frameLabel(state.frame)}",
			onClick = { expanded = true },
			modifier = Modifier
				.testTag(TIME_LINE_DRAWING_MENU_TAG)
				.then(if (bandDescription != null) Modifier.semantics { contentDescription = bandDescription } else Modifier),
		)
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			MenuSection(Res.string.timeline_drawing_menu_view.get())
			TimelineView.entries.forEach { view ->
				MenuChoice(label = viewLabel(view), selected = view == state.view) {
					onView(view)
					expanded = false
				}
			}
			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			MenuSection(Res.string.timeline_drawing_menu_frame.get())
			listOf(TimelineFrame.Everything, TimelineFrame.Story).forEach { frame ->
				MenuChoice(
					label = frameLabel(frame),
					selected = frame == state.frame,
					enabled = frame != TimelineFrame.Story || state.storyFrameAvailable,
				) {
					onFrame(frame)
					expanded = false
				}
			}
		}
	}
}

@Composable
private fun MenuSection(title: String) {
	HdMonoLabel(text = title, modifier = Modifier.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.S))
}

@Composable
private fun MenuChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
	DropdownMenuItem(
		text = {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyMedium,
				color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
				fontWeight = if (selected) FontWeight.Bold else null,
			)
		},
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier.testTag(menuChoiceTag(label)),
	)
}

internal fun menuChoiceTag(label: String) = "Timeline Drawing Menu: $label"

private const val BAND_CELL_CHARS = 18


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
	} else if (row.subjectArchived) {
		HdMonoLabel(
			text = Res.string.timeline_subject_archived.get(),
			color = LocalHammerColors.current.warning,
			modifier = Modifier
				.padding(start = Ui.Padding.L, end = Ui.Padding.L, top = Ui.Padding.M)
				.testTag(EVENT_CARD_ARCHIVED_TAG),
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
