package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

const val TIME_LINE_CREATE_TAG = "Timeline Overview Create"
const val TIME_LINE_LIST_TAG = "Timeline Overview List"
const val EVENT_CARD_TAG = "Timeline Event Card"
const val EVENT_CARD_DATE_TAG = "Timeline Event Card Date"
const val EVENT_CARD_CONTENT_TAG = "Timeline Event Card Content"
const val EVENT_CARD_MAX_CONTENT_LENGTH = 256

private enum class TimeLineSortMode(
	val labelRes: StringResource,
	val glyphRes: StringResource,
) {
	SeqAsc(Res.string.timeline_sort_chronological, Res.string.timeline_sort_glyph_seq_asc),
	SeqDesc(Res.string.timeline_sort_reverse, Res.string.timeline_sort_glyph_seq_desc),
	TitleAsc(Res.string.timeline_sort_title_az, Res.string.timeline_sort_glyph_title_az),
}

private fun TimeLineEvent.firstLine(): String =
	content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

private fun applySort(events: List<TimeLineEvent>, mode: TimeLineSortMode): List<TimeLineEvent> =
	when (mode) {
		TimeLineSortMode.SeqAsc -> events.sortedBy { it.order }
		TimeLineSortMode.SeqDesc -> events.sortedByDescending { it.order }
		TimeLineSortMode.TitleAsc -> events.sortedBy { it.firstLine().lowercase() }
	}

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

	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortMode by remember { mutableStateOf(TimeLineSortMode.SeqAsc) }
	var activeTags by remember { mutableStateOf<Set<String>>(emptySet()) }
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val events = state.timeLine?.events ?: emptyList()

	val visibleEvents by remember(events, searchQuery, sortMode, activeTags) {
		derivedStateOf {
			val byText = if (searchQuery.isBlank()) {
				events
			} else {
				events.filter { event ->
					event.content.contains(searchQuery.trim(), ignoreCase = true) ||
						event.date?.contains(searchQuery.trim(), ignoreCase = true) == true
				}
			}
			val byTags = if (activeTags.isEmpty()) {
				byText
			} else {
				byText.filter { event -> activeTags.all { it in event.tags } }
			}
			applySort(byTags, sortMode)
		}
	}

	val toggleTag: (String) -> Unit = { tag ->
		activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag
	}
	val clearTags: () -> Unit = { activeTags = emptySet() }

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
				Row(
					modifier = Modifier.fillMaxSize(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					HdSearchField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						placeholder = stringResource(Res.string.timeline_search_placeholder),
						onClear = { searchQuery = "" },
						clearContentDescription =
							stringResource(Res.string.timeline_search_clear),
						modifier = Modifier.weight(1f),
					)
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.timeline_search_close.get(),
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
						section = 4,
						title = stringResource(Res.string.timeline_header),
						modifier = Modifier.weight(1f),
						trailing = {
							if (isWide) {
								HdMonoLabel(
									text = stringResource(
										Res.string.timeline_header_meta,
										events.size,
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
								contentDescription = Res.string.timeline_search_button.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						}
					}
				}
			}
		}

		HdFolioDivider()

		CollapsingStrip(scrollBehavior = scrollBehavior) {
			val searchField: @Composable () -> Unit = {
				HdSearchField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					modifier = Modifier
						.then(if (isExpanded) Modifier.width(280.dp) else Modifier.fillMaxWidth()),
					placeholder = stringResource(Res.string.timeline_search_placeholder),
					onClear = { searchQuery = "" },
					clearContentDescription =
						stringResource(Res.string.timeline_search_clear),
				)
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

			TagFilterBar(
				tags = tagIndex,
				total = events.size,
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
					hits = visibleEvents.size,
					total = events.size,
					onToggle = toggleTag,
					onClear = clearTags,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}

		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.testTag(TIME_LINE_LIST_TAG),
			contentPadding = PaddingValues(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
		) {
			if (visibleEvents.isEmpty()) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = Ui.Padding.XXL),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = stringResource(Res.string.timeline_no_events),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
					}
				}
			}

			items(
				items = visibleEvents,
				key = { event -> event.id },
			) { event ->
				val isLast = visibleEvents.lastOrNull()?.id == event.id
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.widthIn(max = 980.dp),
				) {
					EventCard(
						event = event,
						isLast = isLast,
						activeTags = activeTags,
						onTagClick = toggleTag,
						onClick = { viewEvent(event.id) },
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventCard(
	event: TimeLineEvent,
	isLast: Boolean,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	onClick: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val hammerColors = LocalHammerColors.current
	val railColor = MaterialTheme.colorScheme.outlineVariant
	val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
	val surfaceColor = MaterialTheme.colorScheme.surface
	val railColumnWidth = 40.dp
	val railGutter = Ui.Padding.L

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
			Column(
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
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdMonoLabel(
						text = event.date?.takeIf { it.isNotBlank() }
							?: stringResource(Res.string.timeline_view_undated),
						modifier = Modifier
							.sharedElement(
								sharedContentState = rememberSharedContentState(key = "timeline-date-${event.id}"),
								animatedVisibilityScope = animatedVisibilityScope,
							)
							.testTag(EVENT_CARD_DATE_TAG),
					)
					Spacer(modifier = Modifier.weight(1f))
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				val previewMarkdown by remember(event.content) {
					derivedStateOf {
						val trimmed = event.content.trim()
						if (trimmed.length > EVENT_CARD_MAX_CONTENT_LENGTH) {
							trimmed.substring(0, EVENT_CARD_MAX_CONTENT_LENGTH - 1) + "…"
						} else {
							trimmed
						}
					}
				}
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
}

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

@Composable
private fun TagFilterBar(
	tags: List<TagCount>,
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
			label = "${stringResource(Res.string.timeline_filter_all)} · $total",
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
				text = stringResource(Res.string.timeline_filter_filtered, hits, total),
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
				HdMonoLabel(text = stringResource(Res.string.timeline_filter_clear_all))
			}
		}
	}
}

@Composable
private fun SortMenuButton(
	sortMode: TimeLineSortMode,
	onSortChange: (TimeLineSortMode) -> Unit,
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
				text = stringResource(Res.string.timeline_sort_label),
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
			TimeLineSortMode.entries.forEach { mode ->
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
