package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitRegion
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout

const val TIMELINE_DRAWING_TAG = "Timeline Drawing"

/** The horizontal scroll a [TimelineDrawingPane] adds when its drawing is wider than the pane. */
const val TIMELINE_HORIZONTAL_SCROLL_TAG = "Timeline Horizontal Scroll"

/** Each hit region is tagged with this prefix and its target, e.g. `Timeline Hit Ledger 4`. */
const val TIMELINE_HIT_TAG = "Timeline Hit"

/** The event a target stands for; null for a lane header. */
internal val HitTarget.eventId: Int?
	get() = when (this) {
		is HitTarget.Band -> eventId
		is HitTarget.Point -> eventId
		is HitTarget.Ledger -> eventId
		is HitTarget.LaneHeader -> null
	}

fun HitTarget.testTag(): String = when (this) {
	is HitTarget.Band -> "$TIMELINE_HIT_TAG Band $eventId"
	is HitTarget.Point -> "$TIMELINE_HIT_TAG Point $eventId"
	is HitTarget.Ledger -> "$TIMELINE_HIT_TAG Ledger $eventId"
	is HitTarget.LaneHeader -> "$TIMELINE_HIT_TAG Lane $lane"
}

/**
 * A dated timeline drawn from [layout]: one canvas for the ink plus a transparent hit layer with a
 * button per [HitRegion]. [laneColor] maps a layout lane index to its colour; [titles] names events
 * for screen readers and tooltips where the layout only holds a fitted label. A long press on an
 * event peeks at it: the event is projected across the lanes until the press is released, or until a
 * relayout drops or moves the held box. [initialPeekId] starts the drawing peeking at an event, for
 * previews. Hovering an event on desktop, or a long press on touch, shows [hoverCard] for it in a
 * rich tooltip at the event, after telling [onHoverStart] so the card's content can be fetched.
 * [eventSource] is drawn inside each event's mark, the first hit box drawn for it, so a transition can
 * start from it. [highlighted] events are ringed in the accent colour, and [focusedId] more heavily,
 * as search matches.
 */
@Composable
fun TimelineDrawing(
	layout: TimelineLayout,
	laneColor: (Int) -> Color,
	onTap: (HitTarget) -> Unit,
	modifier: Modifier = Modifier,
	initialPeekId: Int? = null,
	titles: Map<Int, String> = emptyMap(),
	laneHeaderDescription: (String) -> String = { it },
	measurer: ComposeTimelineTextMeasurer = rememberTimelineTextMeasurer(),
	hoverCard: (@Composable (eventId: Int) -> Unit)? = null,
	onHoverStart: (eventId: Int) -> Unit = {},
	eventSource: (@Composable BoxScope.(eventId: Int) -> Unit)? = null,
	highlighted: Set<Int> = emptySet(),
	focusedId: Int? = null,
) {
	val scheme = MaterialTheme.colorScheme
	val ink = remember(scheme.surface, scheme.onSurface, scheme.onSurfaceVariant, scheme.outlineVariant, scheme.primary) {
		TimelineInk(scheme.surface, scheme.onSurface, scheme.onSurfaceVariant, scheme.outlineVariant, scheme.primary)
	}
	val descriptions = remember(layout, titles) { describeTargets(layout, titles) }
	// Outlives the draw cache, which a peek, hover or search change rebuilds.
	val texts = remember(layout, measurer) { HashMap<Pair<String, LabelStyle>, TextLayoutResult>() }
	val marks = remember(layout) {
		val seen = HashSet<Int>()
		layout.hitRegions.mapIndexedNotNullTo(HashSet()) { i, region -> i.takeIf { region.target.eventId?.let(seen::add) == true } }
	}
	var hoveredLedgerId by remember { mutableStateOf<Int?>(null) }
	// The pressed hit box, not its event: an event's mark and its label are separate boxes.
	var peeked by remember {
		mutableStateOf(initialPeekId?.let { id -> layout.hitRegions.firstOrNull { it.target.eventId == id } })
	}
	// Only a box this layout still draws, where it was pressed, is held; any other peek ends.
	val held = remember(layout, peeked) { peeked?.takeIf { it in layout.hitRegions } }
	LaunchedEffect(held) {
		if (held == null) peeked = null
	}
	// Watches every gesture without consuming it, so a peek ends on release wherever the pointer is.
	val peekRelease = Modifier.pointerInput(Unit) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
			do {
				val event = awaitPointerEvent(PointerEventPass.Final)
			} while (event.changes.any { it.pressed })
			peeked = null
		}
	}

	Box(modifier = modifier.fillMaxWidth().height(layout.heightDp.dp).then(peekRelease)) {
		Spacer(
			modifier = Modifier
				.matchParentSize()
				.drawWithCache {
					val painter = TimelinePainter(layout, ink, laneColor, held?.target?.eventId, measurer, texts, highlighted, focusedId)
					// Read while drawing, so a hover only redraws.
					onDrawBehind { with(painter) { draw(hoveredLedgerId) } }
				},
		)
		HitLayer(regions = layout.hitRegions, modifier = Modifier.matchParentSize()) { index, region ->
			val target = region.target
			HitBox(
				region = region,
				source = eventSource?.let { source ->
					target.eventId?.takeIf { index in marks }?.let { id -> { source(id) } }
				},
				description = descriptions.describe(target, laneHeaderDescription),
				tooltip = when {
					target.eventId == null -> null
					hoverCard != null -> HitTooltip.Card(hoverCard)
					hoverTooltips && (target is HitTarget.Ledger || target is HitTarget.Band) -> HitTooltip.Description
					else -> null
				},
				peeked = region == held,
				onTap = onTap,
				onPeek = { id ->
					peeked = region
					onHoverStart(id)
				},
				onHover = if (hoverTooltips && target.eventId != null) {
					{ id, hovered ->
						if (hovered) onHoverStart(id)
						if (target is HitTarget.Ledger) {
							if (hovered) hoveredLedgerId = id else if (hoveredLedgerId == id) hoveredLedgerId = null
						}
					}
				} else {
					null
				},
			)
		}
	}
}

/**
 * [TimelineDrawing] in a vertical scroll with the platform scrollbar. A non-null [contentWidth]
 * draws at that width and scrolls horizontally as well. [topPadding] is blank space above the drawing,
 * inside the scroll, for an overlay at the top of the pane to rest over.
 */
@Composable
fun TimelineDrawingPane(
	layout: TimelineLayout,
	laneColor: (Int) -> Color,
	onTap: (HitTarget) -> Unit,
	modifier: Modifier = Modifier,
	initialPeekId: Int? = null,
	contentWidth: Dp? = null,
	titles: Map<Int, String> = emptyMap(),
	laneHeaderDescription: (String) -> String = { it },
	measurer: ComposeTimelineTextMeasurer = rememberTimelineTextMeasurer(),
	scrollState: ScrollState = rememberScrollState(),
	topPadding: Dp = 0.dp,
	hoverCard: (@Composable (eventId: Int) -> Unit)? = null,
	onHoverStart: (eventId: Int) -> Unit = {},
	eventSource: (@Composable BoxScope.(eventId: Int) -> Unit)? = null,
	highlighted: Set<Int> = emptySet(),
	focusedId: Int? = null,
) {
	val horizontalState = rememberScrollState()
	val horizontal = if (contentWidth != null) {
		Modifier.testTag(TIMELINE_HORIZONTAL_SCROLL_TAG).horizontalScroll(horizontalState)
	} else {
		Modifier
	}
	Box(modifier = modifier) {
		Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).then(horizontal)) {
			Spacer(modifier = Modifier.height(topPadding))
			TimelineDrawing(
				layout = layout,
				laneColor = laneColor,
				onTap = onTap,
				modifier = if (contentWidth != null) Modifier.width(contentWidth) else Modifier,
				initialPeekId = initialPeekId,
				titles = titles,
				laneHeaderDescription = laneHeaderDescription,
				measurer = measurer,
				hoverCard = hoverCard,
				onHoverStart = onHoverStart,
				eventSource = eventSource,
				highlighted = highlighted,
				focusedId = focusedId,
			)
		}
		MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
	}
}

private class TargetDescriptions(
	val ledger: Map<Int, String>,
	val spans: Map<Int, String>,
	val points: Map<Int, String>,
	val titles: Map<Int, String>,
) {
	fun describe(target: HitTarget, laneHeader: (String) -> String): String = when (target) {
		is HitTarget.LaneHeader -> laneHeader(target.lane)
		is HitTarget.Ledger -> ledger[target.eventId] ?: titles[target.eventId].orEmpty()
		is HitTarget.Band -> titles[target.eventId] ?: spans[target.eventId] ?: ledger[target.eventId].orEmpty()
		is HitTarget.Point -> titles[target.eventId] ?: points[target.eventId] ?: ledger[target.eventId].orEmpty()
	}
}

private fun describeTargets(layout: TimelineLayout, titles: Map<Int, String>): TargetDescriptions {
	val spans = HashMap<Int, String>()
	for (band in layout.bands) {
		when (val label = band.label) {
			is BandLabel.Lines -> spans[band.eventId] = label.lines.joinToString(" ")
			is BandLabel.Rotated -> spans[band.eventId] = label.text
			is BandLabel.Leader -> spans[band.eventId] = label.label.fullText
			BandLabel.None -> Unit
		}
	}
	for (bar in layout.bars) bar.label?.let { spans[bar.eventId] = it.fullText }
	val points = HashMap<Int, String>()
	for (point in layout.points) point.label?.let { points[point.eventId] = it.fullText }
	return TargetDescriptions(
		ledger = layout.ledgerRows.associate { it.eventId to it.fullText },
		spans = spans,
		points = points,
		titles = titles.filterValues { it.isNotBlank() },
	)
}
