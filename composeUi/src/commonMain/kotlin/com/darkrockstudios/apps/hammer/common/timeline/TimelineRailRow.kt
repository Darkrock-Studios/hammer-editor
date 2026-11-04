package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.eventExcerpt
import com.darkrockstudios.apps.hammer.common.compose.LocalImageNames
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDragHandle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.timeline_lane_swatch_description
import com.darkrockstudios.apps.hammer.timeline_milestone_description
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

private val WhenColumnWidth = 128.dp
private val SpineX = 10.dp
private val RailStart = 24.dp
private val RailPitch = 22.dp
private val RailWidth = 2.dp
private val MarkSize = 14.dp
private val MarkTop = 13.dp
private val RuleY = 20.dp
private val GripWidth = 34.dp
private const val RAIL_ALPHA = 0.22f
private const val RULE_ALPHA = 0.35f

/** The gutter's width: the spine, then one rail per lane. */
internal fun railGutterWidth(laneCount: Int): Dp = if (laneCount == 0) RailStart - 2.dp else RailStart + RailPitch * laneCount

/**
 * One entry of a timeline drawn as a list, in the drawing's vocabulary: the "when" where the axis
 * labels sit, a spine with one rail per lane ([laneCount] of them), the entry's mark on its lane's
 * rail (or on the spine), then a mono kind line, the title and one line of excerpt. A milestone rules
 * a dotted line across the row. Without [whenColumn] the "when" joins the kind line, as on a phone.
 *
 * A plain event's excerpt comes from its own text; a placement's is [placementExcerpt], read from its
 * subject. On a [dated] timeline an entry with no start says it is undated.
 *
 *     LATE OCTOBER  │ ◆ │   EVENT
 *                   │ │ │   The first snow
 *                   │ │ │   Snow on the pass three weeks early.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TimelineRailRow(
	row: TimelineRow,
	laneCount: Int,
	whenColumn: Boolean,
	onClick: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	dated: Boolean = false,
	isDraggable: Boolean = false,
	placementExcerpt: String? = null,
	words: StoryDateWords = rememberStoryDateWords(),
) {
	val event = row.event
	val start = row.start
	val whenText = when {
		start != null -> formatStoryDateRange(row.calendar ?: CalendarDef(), start, row.end, words)
		dated -> row.whenText.ifBlank { Res.string.timeline_view_undated.get() }
		else -> row.whenText
	}
	val imageNames = LocalImageNames.current
	val excerpt = if (event.kind.isPlacement) {
		placementExcerpt
	} else {
		remember(event.content, event.title, row.title, imageNames) {
			// A plain event without a title is titled by its first line, which the excerpt then skips.
			eventExcerpt(event.content, skipTitle = row.title.takeIf { event.title.isNullOrBlank() }, imageNames)
		}
	}
	val ink = MaterialTheme.colorScheme.onSurface
	val hairline = MaterialTheme.colorScheme.outlineVariant

	with(sharedTransitionScope) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "timeline-card-${event.id}"),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.clickable(onClick = onClick)
				.testTag(EVENT_CARD_TAG),
		) {
			if (event.milestone) {
				MilestoneRule(
					color = ink,
					modifier = Modifier.padding(start = if (whenColumn) WhenColumnWidth else 0.dp),
				)
			}
			Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
				if (whenColumn) {
					HdMonoLabel(
						text = whenText,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						textAlign = TextAlign.End,
						modifier = Modifier
							.width(WhenColumnWidth)
							.padding(start = 8.dp, end = 10.dp, top = 12.dp)
							.sharedElement(
								sharedContentState = rememberSharedContentState(key = "timeline-date-${event.id}"),
								animatedVisibilityScope = animatedVisibilityScope,
							)
							.testTag(EVENT_CARD_DATE_TAG),
					)
				}
				RailGutter(row = row, laneCount = laneCount)
				Column(
					modifier = Modifier
						.weight(1f)
						.drawBehind {
							drawLine(
								color = hairline,
								start = Offset(0f, size.height - 0.5f),
								end = Offset(size.width, size.height - 0.5f),
								strokeWidth = 1f,
							)
						}
						.padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
				) {
					KindLine(row = row, whenText = whenText.takeUnless { whenColumn })
					if (row.title.isNotBlank()) {
						Text(
							text = row.title,
							style = MaterialTheme.typography.titleMedium.let {
								if (event.milestone) it.copy(fontWeight = FontWeight.Bold) else it
							},
							color = ink,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(top = 1.dp).testTag(EVENT_CARD_TITLE_TAG),
						)
					}
					if (!excerpt.isNullOrBlank()) {
						Text(
							text = excerpt,
							style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(top = 2.dp).testTag(EVENT_CARD_CONTENT_TAG),
						)
					}
				}
				if (isDraggable) {
					Box(
						modifier = Modifier.width(GripWidth).fillMaxHeight(),
						contentAlignment = Alignment.Center,
					) {
						HdDragHandle()
					}
				}
			}
		}
	}
}

/** The kind, a scene's chapter, and on a phone the "when" ahead of it and the lane after it. */
@Composable
private fun KindLine(row: TimelineRow, whenText: String?) {
	val event = row.event
	val warning = when {
		row.subjectMissing -> Res.string.timeline_subject_missing.get()
		row.subjectArchived -> Res.string.timeline_subject_archived.get()
		else -> null
	}
	val parts = buildList {
		if (!whenText.isNullOrBlank()) add(whenText)
		add(if (event.kind.isPlacement) "${event.kind.hdMark()} ${event.kind.label()}" else event.kind.label())
		if (warning == null) row.subtitle?.let(::add)
		if (whenText != null) event.lane?.takeIf { it.isNotBlank() }?.let(::add)
	}
	// A milestone's rule runs through this line; the text knocks it out.
	Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
		HdMonoLabel(
			text = parts.joinToString(" · "),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.weight(1f, fill = false)
				.then(if (whenText != null) Modifier.testTag(EVENT_CARD_DATE_TAG) else Modifier),
		)
		if (warning != null) {
			HdMonoLabel(
				text = " · $warning",
				color = LocalHammerColors.current.warning,
				maxLines = 1,
				modifier = if (row.subjectArchived) Modifier.testTag(EVENT_CARD_ARCHIVED_TAG) else Modifier,
			)
		}
	}
}

/** The spine and every lane's rail, with this entry's mark on its own rail or on the spine. */
@Composable
private fun RailGutter(row: TimelineRow, laneCount: Int) {
	val colors = LocalHammerColors.current
	val spine = MaterialTheme.colorScheme.outline
	val laneIndex = row.laneIndex
	val railColors = List(laneCount) { colors.laneColor(it) }
	Box(
		modifier = Modifier
			.width(railGutterWidth(laneCount))
			.fillMaxHeight()
			.drawBehind {
				val spineX = SpineX.toPx()
				drawLine(spine, Offset(spineX, 0f), Offset(spineX, size.height), strokeWidth = 1.dp.toPx())
				railColors.forEachIndexed { i, color ->
					val x = (RailStart + RailPitch * i + RailPitch / 2).toPx()
					drawLine(
						color = color.copy(alpha = RAIL_ALPHA),
						start = Offset(x, 0f),
						end = Offset(x, size.height),
						strokeWidth = RailWidth.toPx(),
					)
				}
			},
	) {
		val centre = if (laneIndex != null) RailStart + RailPitch * laneIndex + RailPitch / 2 else SpineX
		val markColor = laneIndex?.let(colors::laneColor)
		val laneDescription = row.event.lane?.let { Res.string.timeline_lane_swatch_description.get(it) }
		Box(
			modifier = Modifier
				.offset(x = centre - MarkSize / 2, y = MarkTop)
				.size(MarkSize)
				.then(
					if (laneDescription != null && markColor != null) {
						Modifier
							.semantics { contentDescription = laneDescription }
							.testTag(EVENT_CARD_LANE_TAG)
					} else {
						Modifier
					}
				),
			contentAlignment = Alignment.Center,
		) {
			RailMark(kind = row.event.kind, milestone = row.event.milestone, color = markColor)
		}
	}
}

/**
 * The drawing's marks: a diamond for a plain event, the kind's glyph in a box for a placement, an
 * ink diamond for a milestone of any kind. [color] is the lane's, or null on the spine.
 */
@Composable
private fun RailMark(kind: TimelineKind, milestone: Boolean, color: Color?) {
	val description = kind.label()
	val surface = MaterialTheme.colorScheme.surface
	val ink = MaterialTheme.colorScheme.onSurface
	val modifier = Modifier
		.semantics { contentDescription = description }
		.testTag(EVENT_CARD_KIND_TAG)
	when {
		milestone -> Box(
			modifier = modifier
				.size(MarkSize)
				.rotate(45f)
				.padding(1.dp)
				.background(surface, RectangleShape)
				.padding(1.dp)
				.background(ink, RectangleShape),
		)

		kind == TimelineKind.EVENT -> Box(
			modifier = modifier
				.size(MarkSize)
				.rotate(45f)
				.padding(2.dp)
				.background(surface, RectangleShape)
				.padding(1.dp)
				.background(ink, RectangleShape)
				.padding(1.dp)
				.background(color ?: MaterialTheme.colorScheme.onSurfaceVariant, RectangleShape),
		)

		else -> Box(
			modifier = modifier
				.size(MarkSize)
				.background(surface, RectangleShape)
				.border(1.dp, color ?: ink, RectangleShape),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = kind.hdMark(),
				style = MaterialTheme.typography.labelSmall,
				color = color ?: ink,
			)
		}
	}
}

@Composable
private fun MilestoneRule(color: Color, modifier: Modifier = Modifier) {
	val description = Res.string.timeline_milestone_description.get()
	Box(
		modifier = modifier
			.fillMaxWidth()
			.padding(top = RuleY)
			.height(1.dp)
			.drawBehind {
				drawLine(
					color = color.copy(alpha = RULE_ALPHA),
					start = Offset(0f, 0f),
					end = Offset(size.width, 0f),
					strokeWidth = 1.dp.toPx(),
					pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
				)
			}
			.semantics { contentDescription = description }
			.testTag(EVENT_CARD_MILESTONE_TAG),
	)
}
