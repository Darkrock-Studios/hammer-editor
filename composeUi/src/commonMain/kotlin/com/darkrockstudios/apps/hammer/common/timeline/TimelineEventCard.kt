package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDragHandle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.rememberMarkdownPreview
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.timeline_entry_subtitle
import com.darkrockstudios.apps.hammer.timeline_subject_archived
import com.darkrockstudios.apps.hammer.timeline_subject_missing
import com.darkrockstudios.apps.hammer.timeline_view_undated

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
	words: StoryDateWords = rememberStoryDateWords(),
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
						words = words,
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
	words: StoryDateWords,
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
