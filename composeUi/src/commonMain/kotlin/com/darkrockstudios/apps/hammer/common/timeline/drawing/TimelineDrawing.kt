package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_SCENE_GLYPH
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LaneShade
import com.darkrockstudios.apps.hammer.common.compose.theme.hueDegrees
import com.darkrockstudios.apps.hammer.common.compose.theme.laneShade
import com.darkrockstudios.apps.hammer.common.timeline.layout.BandLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitRegion
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelFitter
import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.LayoutMetrics
import com.darkrockstudios.apps.hammer.common.timeline.layout.LeaderLabel
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerMark
import com.darkrockstudios.apps.hammer.common.timeline.layout.LedgerStyle
import com.darkrockstudios.apps.hammer.common.timeline.layout.PointDp
import com.darkrockstudios.apps.hammer.common.timeline.layout.PointGlyph
import com.darkrockstudios.apps.hammer.common.timeline.layout.RectDp
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

private val LedgerLineHeight = LayoutMetrics().ledgerLineHeight
private const val LANE_INSET = 8f
private const val HEADER_BASELINE = 13f
private const val CAP_Y = 18f
private const val CAP_HEIGHT = 2f
private const val RAIL_CAP_INSET = 3f
private const val AXIS_TOP = 24f
private const val AXIS_LABEL_GAP = 10f
private const val AXIS_TICK = 4f
private const val FOLD_HALF = 3f
private const val LEDGER_MARK_INSET = 4f
private const val LEDGER_TICK = 4f
private const val MATCH_RING = 1.5f
private const val MATCH_RING_FOCUSED = 3f
private const val MATCH_GAP = 2f
private const val MATCH_VEIL_ALPHA = .62f
private const val AXIS_SEPARATOR = " · "

/** [color]'s opposite on the colour wheel, saturated and mid-light so it reads on light and dark. */
private fun complement(color: Color): Color =
	Color.hsl((hueDegrees(color) + 180f) % 360f, saturation = .85f, lightness = .6f)

// The leader's elbow sits this far above a ledger row's first baseline.
private const val LEDGER_LINE_MID = 4f

// The desktop scrollbar takes a gutter; touch platforms overlay a transient one. Hover tooltips
// follow the same split.
private val hoverTooltips: Boolean get() = MpScrollBarGutter > 0.dp

/** The theme colours a drawing uses, read once per composition. */
@Immutable
private class TimelineInk(
	val surface: Color,
	val onSurface: Color,
	val onSurfaceVariant: Color,
	val outlineVariant: Color,
	val primary: Color,
)

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
 * Places one child per region at the region's rect. Placing the child itself, rather than offsetting
 * it, keeps a tooltip's anchor on the region. Each [content] call must emit exactly one layout node.
 */
@Composable
private fun HitLayer(
	regions: List<HitRegion>,
	modifier: Modifier = Modifier,
	content: @Composable (index: Int, region: HitRegion) -> Unit,
) {
	Layout(
		modifier = modifier,
		// Keyed so a box keeps its gesture state when a relayout reorders the regions.
		content = { regions.forEachIndexed { i, region -> key(region.target) { content(i, region) } } },
	) { measurables, constraints ->
		val placeables = measurables.mapIndexed { i, measurable ->
			val rect = regions[i].rect
			val width = rect.width.dp.roundToPx().coerceAtLeast(0)
			val height = rect.height.dp.roundToPx().coerceAtLeast(0)
			measurable.measure(Constraints.fixed(width, height))
		}
		layout(constraints.maxWidth, constraints.maxHeight) {
			placeables.forEachIndexed { i, placeable ->
				val rect = regions[i].rect
				placeable.placeRelative(rect.x.dp.roundToPx(), rect.y.dp.roundToPx())
			}
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

/** What a hit box shows on hover: its description as plain text, or an event's card. */
private sealed interface HitTooltip {
	data object Description : HitTooltip
	data class Card(val content: @Composable (eventId: Int) -> Unit) : HitTooltip
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HitBox(
	region: HitRegion,
	source: (@Composable BoxScope.() -> Unit)?,
	description: String,
	tooltip: HitTooltip?,
	peeked: Boolean,
	onTap: (HitTarget) -> Unit,
	onPeek: (eventId: Int) -> Unit,
	onHover: ((eventId: Int, hovered: Boolean) -> Unit)? = null,
) {
	val eventId = region.target.eventId
	val currentOnHover by rememberUpdatedState(onHover)

	val hover = if (onHover != null && eventId != null) {
		Modifier.pointerInput(eventId) {
			awaitPointerEventScope {
				while (true) {
					when (awaitPointerEvent().type) {
						PointerEventType.Enter -> currentOnHover?.invoke(eventId, true)
						PointerEventType.Exit -> currentOnHover?.invoke(eventId, false)
					}
				}
			}
		}
	} else {
		Modifier
	}
	val target = Modifier
		.testTag(region.target.testTag())
		.then(hover)
		.semantics { contentDescription = description }
		.combinedClickable(
			role = Role.Button,
			onLongClick = eventId?.let { id -> { onPeek(id) } },
			onClick = { onTap(region.target) },
		)
	val tooltipContent: (@Composable TooltipScope.() -> Unit)? = when {
		tooltip is HitTooltip.Card && eventId != null -> {
			{
				RichTooltip(
					modifier = Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
					shape = RectangleShape,
					colors = TooltipDefaults.richTooltipColors(
						containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
						contentColor = MaterialTheme.colorScheme.onSurface,
					),
					shadowElevation = 0.dp,
				) {
					tooltip.content(eventId)
				}
			}
		}

		tooltip == HitTooltip.Description && description.isNotBlank() -> {
			{ PlainTooltip { Text(description) } }
		}

		else -> null
	}
	if (tooltipContent != null) {
		// Hover drives the tooltip where a pointer hovers; on touch it shows while a long press holds.
		val state = rememberTooltipState(isPersistent = !hoverTooltips)
		if (!hoverTooltips) {
			LaunchedEffect(peeked) {
				if (peeked) state.show() else state.dismiss()
			}
		}
		TooltipBox(
			positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
			tooltip = tooltipContent,
			state = state,
			modifier = Modifier.fillMaxSize(),
			enableUserInput = hoverTooltips,
		) {
			Box(modifier = Modifier.fillMaxSize().then(target)) { source?.invoke(this) }
		}
	} else {
		Box(modifier = Modifier.fillMaxSize().then(target)) { source?.invoke(this) }
	}
}

/** Draws [layout] in z order: chrome, spans, folds, labels, points, rules, ledger, projection, matches. */
private class TimelinePainter(
	private val layout: TimelineLayout,
	private val ink: TimelineInk,
	private val laneColor: (Int) -> Color,
	private val peekedId: Int?,
	private val measurer: ComposeTimelineTextMeasurer,
	private val texts: HashMap<Pair<String, LabelStyle>, TextLayoutResult>,
	private val highlighted: Set<Int>,
	private val focusedId: Int?,
) {
	private var glyphText: TextLayoutResult? = null

	/** [hoveredLedgerId] is the ledger row under the pointer, projected more lightly than the peek. */
	fun DrawScope.draw(hoveredLedgerId: Int?) {
		drawChrome()
		for (band in layout.bands) drawBand(band.rect, band.laneIndex, band.shadeIndex, band.circa, band.eventId)
		for (bar in layout.bars) drawBar(bar.rect, bar.laneIndex, bar.circa, bar.eventId)
		drawFolds()
		drawSpanLabels()
		for (point in layout.points) drawPoint(point.x, point.y, point.glyph, point.laneIndex, point.eventId)
		for (point in layout.points) point.label?.let { drawLeaderLabel(it, laneColor(point.laneIndex)) }
		drawMilestoneRules()
		drawLedger()
		if (hoveredLedgerId != peekedId) drawHoverProjection(hoveredLedgerId)
		drawProjection()
		drawMatches()
	}

	/**
	 * Spotlights the search matches: everything else sinks under a veil of the surface colour, every
	 * match keeps its full colour and is ringed in the accent, and the focused match takes a heavy
	 * ring in the hue opposite its lane's, which no lane shade can blend into.
	 */
	private fun DrawScope.drawMatches() {
		if (highlighted.isEmpty()) return
		// Cut-outs are joined first, so matches that touch or overlap stay clear between them.
		val clear = Path()
		for (region in layout.hitRegions) {
			if (region.target.eventId !in highlighted) continue
			val r = region.rect
			clear.addRect(Rect(px(r.x - MATCH_GAP), px(r.y - MATCH_GAP), px(r.right + MATCH_GAP), px(r.y + r.height + MATCH_GAP)))
		}
		val veil = Path().apply {
			op(Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }, clear, PathOperation.Difference)
		}
		drawPath(veil, ink.surface.copy(alpha = MATCH_VEIL_ALPHA))

		val ringed = HashSet<Int>()
		for (region in layout.hitRegions) {
			val id = region.target.eventId ?: continue
			if (id !in highlighted || !ringed.add(id)) continue
			val focused = id == focusedId
			val width = if (focused) MATCH_RING_FOCUSED else MATCH_RING
			val color = if (focused) laneIndexOf(id)?.let { complement(laneColor(it)) } ?: ink.primary else ink.primary
			val r = region.rect
			val out = MATCH_GAP + width / 2f
			drawRect(
				color,
				at(r.x - out, r.y - out),
				Size(px(r.width + 2 * out), px(r.height + 2 * out)),
				style = Stroke(width = px(width)),
			)
		}
	}

	private val laneByEvent: Map<Int, Int> by lazy {
		buildMap {
			layout.bands.forEach { put(it.eventId, it.laneIndex) }
			layout.bars.forEach { put(it.eventId, it.laneIndex) }
			layout.points.forEach { put(it.eventId, it.laneIndex) }
		}
	}

	private fun laneIndexOf(eventId: Int): Int? = laneByEvent[eventId]

	private fun DrawScope.px(dp: Float): Float = dp * density

	private fun DrawScope.at(x: Float, y: Float): Offset = Offset(px(x), px(y))

	private fun text(text: String, style: LabelStyle): TextLayoutResult =
		texts.getOrPut(text to style) { measurer.measure(text, style) }

	/** With [halo], the text is ringed in the surface colour so it reads over a fill or a rule. */
	private fun DrawScope.drawTextAt(result: TextLayoutResult, x: Float, baseline: Float, color: Color, halo: Boolean = false) {
		val topLeft = Offset(px(x), px(baseline) - result.firstBaseline)
		if (halo) for (offset in haloOffsets()) drawText(result, ink.surface, topLeft + offset)
		drawText(result, color, topLeft)
	}

	private fun DrawScope.dotted(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(px(1f), px(2f)))

	private fun DrawScope.hairline(color: Color, x0: Float, y0: Float, x1: Float, y1: Float) {
		drawLine(color, at(x0, y0), at(x1, y1), strokeWidth = Stroke.HairlineWidth)
	}

	private fun DrawScope.polyline(points: List<PointDp>, color: Color, width: Float) {
		if (points.size < 2) return
		val path = Path()
		path.moveTo(px(points[0].x), px(points[0].y))
		for (i in 1 until points.size) path.lineTo(px(points[i].x), px(points[i].y))
		drawPath(path, color, style = Stroke(width = px(width)))
	}

	private fun DrawScope.diamond(cx: Float, cy: Float, half: Float): Path = Path().apply {
		moveTo(px(cx), px(cy - half))
		lineTo(px(cx + half), px(cy))
		lineTo(px(cx), px(cy + half))
		lineTo(px(cx - half), px(cy))
		close()
	}

	private fun DrawScope.drawChrome() {
		val height = layout.heightDp
		val region = layout.laneRegion
		for (lane in layout.lanes) {
			if (lane.index > 0) hairline(ink.outlineVariant, lane.xDp, 0f, lane.xDp, height)
			val header = lane.header
			// A phone rail has no heading, only its cap.
			val inset = if (header != null) LANE_INSET else RAIL_CAP_INSET
			if (header != null) {
				drawTextAt(text(header, LabelStyle.LaneHeader), lane.xDp + LANE_INSET, HEADER_BASELINE, ink.onSurfaceVariant)
			}
			drawRect(
				color = laneColor(lane.index),
				topLeft = at(lane.xDp + inset, CAP_Y),
				size = Size(px(maxOf(0f, lane.widthDp - 2 * inset)), px(CAP_HEIGHT)),
			)
		}
		if (region.width > 0f) hairline(ink.outlineVariant, region.right, 0f, region.right, height)

		val axisX = layout.axis.xDp
		hairline(ink.outlineVariant, axisX, AXIS_TOP, axisX, height)
		val available = axisX - AXIS_LABEL_GAP
		val lineHeight = measurer.lineHeightDp(LabelStyle.Axis)
		var lastBottom = Float.NEGATIVE_INFINITY
		for (tick in layout.axis.ticks) {
			hairline(ink.outlineVariant, axisX - AXIS_TICK, tick.yDp, axisX, tick.yDp)
			val lines = wrapAxisLabel(tick.label, available)
			val top = tick.yDp - lines.size * lineHeight / 2f
			if (top < lastBottom) continue
			lines.forEachIndexed { i, line ->
				val result = text(line, LabelStyle.Axis)
				drawText(result, ink.onSurfaceVariant, Offset(px(available) - result.size.width, px(top + i * lineHeight)))
			}
			lastBottom = top + lines.size * lineHeight
		}
	}

	/** Splits an axis label at its ` · ` separators, or failing those at spaces, until each line fits. */
	private fun wrapAxisLabel(label: String, available: Float): List<String> {
		if (measurer.widthDp(label, LabelStyle.Axis) <= available) return listOf(label)
		val separator = if (AXIS_SEPARATOR in label) AXIS_SEPARATOR else " "
		return LabelFitter.wrapWords(label, available, LabelStyle.Axis, measurer, separator)
	}

	private fun DrawScope.drawBand(rect: RectDp, laneIndex: Int, shadeIndex: Int, circa: Boolean, eventId: Int) {
		val base = laneColor(laneIndex)
		val shade = when (shadeIndex.mod(3)) {
			0 -> LaneShade.Base
			1 -> LaneShade.TowardSurface
			else -> LaneShade.TowardInk
		}
		val fill = laneShade(base, shade, ink.surface, ink.onSurface)
		// Half a dp off each end leaves a 1dp surface gap wherever two segments meet.
		val top = rect.y + .5f
		val height = maxOf(0f, rect.height - 1f)
		if (circa) {
			drawRect(fill.copy(alpha = .2f), at(rect.x, top), Size(px(rect.width), px(height)))
			drawRect(base, at(rect.x, top), Size(px(rect.width), px(height)), style = Stroke(px(1f), pathEffect = dotted()))
		} else {
			drawRect(fill, at(rect.x, top), Size(px(rect.width), px(height)))
		}
		if (eventId == peekedId) drawSelection(rect)
	}

	private fun DrawScope.drawBar(rect: RectDp, laneIndex: Int, circa: Boolean, eventId: Int) {
		val base = laneColor(laneIndex)
		if (circa) {
			drawRect(base.copy(alpha = .2f), at(rect.x, rect.y), Size(px(rect.width), px(rect.height)))
			drawRect(base, at(rect.x, rect.y), Size(px(rect.width), px(rect.height)), style = Stroke(px(1f), pathEffect = dotted()))
		} else {
			drawRect(base.copy(alpha = .7f), at(rect.x, rect.y), Size(px(rect.width), px(rect.height)))
		}
		if (eventId == peekedId) drawSelection(rect)
	}

	private fun DrawScope.drawSelection(rect: RectDp) {
		drawRect(ink.onSurface, at(rect.x, rect.y), Size(px(rect.width), px(rect.height)), style = Stroke(px(1.5f)))
	}

	private fun DrawScope.drawFolds() {
		val axisX = layout.axis.xDp
		val right = layout.widthDp
		for (fold in layout.folds) {
			val mid = (fold.y0 + fold.y1) / 2f
			drawRect(ink.surface, at(axisX - 6f, mid - FOLD_HALF), Size(px(right - axisX + 6f), px(2 * FOLD_HALF)))
			for (y in listOf(mid - FOLD_HALF, mid + FOLD_HALF)) {
				drawLine(ink.outlineVariant, at(axisX, y), at(right, y), strokeWidth = px(1f), pathEffect = dotted())
			}
		}
		for (y in layout.axis.foldGlyphYs) {
			val zigzag = listOf(
				PointDp(axisX, y - 7f),
				PointDp(axisX - 4f, y - 3.5f),
				PointDp(axisX + 4f, y + 0f),
				PointDp(axisX - 4f, y + 3.5f),
				PointDp(axisX, y + 7f),
			)
			polyline(zigzag, ink.onSurfaceVariant, 1f)
		}
	}

	private fun DrawScope.drawSpanLabels() {
		for (band in layout.bands) {
			when (val label = band.label) {
				is BandLabel.Lines -> {
					val style = if (label.small) LabelStyle.BandLabelSmall else LabelStyle.BandLabel
					val step = measurer.lineHeightDp(style)
					label.lines.forEachIndexed { i, line ->
						drawHaloText(text(line, style), label.xDp, label.firstBaselineDp + i * step)
					}
				}

				is BandLabel.Rotated -> {
					val result = text(label.text, LabelStyle.BandLabel)
					val pivot = at(label.cx, label.cy)
					rotate(-90f, pivot) {
						val topLeft = Offset(pivot.x - result.size.width / 2f, pivot.y - result.size.height / 2f)
						for (offset in haloOffsets()) drawText(result, ink.surface, topLeft + offset)
						drawText(result, ink.onSurface, topLeft)
					}
				}

				is BandLabel.Leader -> drawLeaderLabel(label.label, laneColor(band.laneIndex))
				BandLabel.None -> Unit
			}
		}
		for (bar in layout.bars) bar.label?.let { drawLeaderLabel(it, laneColor(bar.laneIndex)) }
	}

	private fun DrawScope.haloOffsets(): List<Offset> {
		val d = px(1f)
		return listOf(Offset(-d, 0f), Offset(d, 0f), Offset(0f, -d), Offset(0f, d))
	}

	/** Text over a lane fill, ringed in the surface colour so it reads on any shade. */
	private fun DrawScope.drawHaloText(result: TextLayoutResult, x: Float, baseline: Float) {
		drawTextAt(result, x, baseline, ink.onSurface, halo = true)
	}

	private fun DrawScope.drawLeaderLabel(label: LeaderLabel, leaderColor: Color) {
		polyline(label.leader, leaderColor.copy(alpha = .7f), 1f)
		label.lines.forEachIndexed { i, line ->
			drawTextAt(text(line, LabelStyle.BarLabel), label.xDp, label.firstBaselineDp + i * LedgerLineHeight, ink.onSurface)
		}
		label.sub?.let { sub ->
			val baseline = label.firstBaselineDp + label.lines.size * LedgerLineHeight
			drawTextAt(text(sub, LabelStyle.LedgerSub), label.xDp, baseline, ink.onSurfaceVariant)
		}
	}

	private fun DrawScope.drawPoint(x: Float, y: Float, glyph: PointGlyph, laneIndex: Int, eventId: Int) {
		val color = laneColor(laneIndex)
		when (glyph) {
			PointGlyph.Diamond -> {
				drawPath(diamond(x, y, 4f), ink.surface)
				val mark = diamond(x, y, 3f)
				drawPath(mark, color)
				drawPath(mark, ink.onSurface, style = Stroke(px(1f)))
			}

			PointGlyph.MilestoneDiamond -> drawMilestoneDiamond(x, y)
			PointGlyph.EntryBox -> {
				val half = 7f
				drawRect(ink.surface, at(x - half, y - half), Size(px(2 * half), px(2 * half)))
				drawRect(color, at(x - half, y - half), Size(px(2 * half), px(2 * half)), style = Stroke(px(1f)))
				val result = glyphText ?: measurer.textMeasurer.measure(
					TimelineKind.ENTRY.hdMark(),
					TextStyle(fontSize = 9.sp),
					density = this,
				).also { glyphText = it }
				drawText(
					result,
					ink.onSurface,
					Offset(px(x) - result.size.width / 2f, px(y) - result.size.height / 2f),
				)
			}
		}
		if (eventId == peekedId) {
			drawRect(ink.onSurface, at(x - 9f, y - 9f), Size(px(18f), px(18f)), style = Stroke(px(1f)))
		}
	}

	private fun DrawScope.drawMilestoneDiamond(x: Float, y: Float) {
		val mark = diamond(x, y, 3.5f)
		drawPath(mark, ink.onSurface)
		drawPath(mark, ink.surface, style = Stroke(px(1.5f)))
	}

	private fun DrawScope.drawMilestoneRules() {
		val color = ink.onSurface.copy(alpha = .45f)
		for (rule in layout.milestoneRules) {
			drawLine(
				color,
				at(layout.axis.xDp, rule.yDp),
				at(layout.widthDp, rule.yDp),
				strokeWidth = px(1f),
				pathEffect = dotted(),
			)
		}
	}

	private fun DrawScope.drawLedger() {
		val markX = layout.ledgerXDp + LEDGER_MARK_INSET
		val cx = markX + 3f
		val boundary = layout.laneRegion.right
		val hasLanes = layout.laneRegion.width > 0f
		for (row in layout.ledgerRows) {
			val y = row.anchorYDp
			if (row.style == LedgerStyle.Muted && hasLanes) {
				hairline(ink.outlineVariant, boundary - LEDGER_TICK, y, boundary + LEDGER_TICK, y)
			}
			val laneInk = row.laneIndex?.let(laneColor)
			row.leader?.let { leader ->
				// A lane callout's leader runs from its mark in the rail, so it takes the lane's colour.
				val leaderColor = if (row.style == LedgerStyle.LaneCallout && laneInk != null) {
					laneInk.copy(alpha = .7f)
				} else {
					ink.onSurfaceVariant.copy(alpha = .4f)
				}
				polyline(leader, leaderColor, 1f)
			}
			when (row.mark) {
				LedgerMark.None -> Unit
				LedgerMark.Pilcrow -> {
					val result = text(HD_SCENE_GLYPH, LabelStyle.LedgerSub)
					drawText(
						result,
						ink.onSurfaceVariant,
						Offset(px(cx) - result.size.width / 2f, px(y) - result.size.height / 2f),
					)
				}

				LedgerMark.MilestoneDiamond -> drawMilestoneDiamond(cx, y)
				LedgerMark.SpanBar -> {
					val y1 = row.anchorY1Dp ?: y
					drawRect(
						ink.onSurfaceVariant.copy(alpha = .6f),
						at(cx - 2f, y),
						Size(px(4f), px(maxOf(1f, y1 - y))),
					)
				}

				LedgerMark.Dot -> drawRect(ink.onSurfaceVariant, at(cx - 2f, y - 2f), Size(px(4f), px(4f)))
				// On the row's own line, where its leader ends.
				LedgerMark.LaneSwatch -> {
					val sy = row.textYDp - LEDGER_LINE_MID
					drawRect(laneInk ?: ink.onSurfaceVariant, at(cx - 3f, sy - 3f), Size(px(6f), px(6f)))
				}
			}

			val (style, color) = when (row.style) {
				LedgerStyle.Muted -> LabelStyle.Ledger to ink.onSurfaceVariant
				LedgerStyle.Milestone -> LabelStyle.LedgerMilestone to ink.onSurface
				LedgerStyle.LaneCallout -> LabelStyle.Ledger to ink.onSurface
			}
			val lineColor = if (row.eventId == peekedId) ink.onSurface else color
			// A milestone's text sits on its own dotted rule.
			val halo = row.style == LedgerStyle.Milestone
			row.lines.forEachIndexed { i, line ->
				drawTextAt(text(line, style), layout.ledgerTextXDp, row.textYDp + i * LedgerLineHeight, lineColor, halo)
			}
			row.sub?.let { sub ->
				val baseline = row.textYDp + row.lines.size * LedgerLineHeight
				drawTextAt(text(sub, LabelStyle.LedgerSub), layout.ledgerTextXDp, baseline, ink.onSurfaceVariant, halo)
			}
		}
	}

	private fun DrawScope.drawHoverProjection(eventId: Int?) {
		val row = eventId?.let { id -> layout.ledgerRows.firstOrNull { it.eventId == id } } ?: return
		val region = layout.laneRegion
		val x0 = region.x
		val x1 = if (region.width > 0f) region.right else layout.widthDp
		row.anchorY1Dp?.let { y1 ->
			drawRect(ink.primary.copy(alpha = .06f), at(x0, row.anchorYDp), Size(px(x1 - x0), px(y1 - row.anchorYDp)))
		}
		drawLine(
			ink.primary.copy(alpha = .5f),
			at(x0, row.anchorYDp),
			at(x1, row.anchorYDp),
			strokeWidth = px(1f),
			pathEffect = dotted(),
		)
	}

	private fun DrawScope.drawProjection() {
		val projection = layout.projectionOf(peekedId) ?: return
		val region = layout.laneRegion
		val x0 = region.x
		val x1 = if (region.width > 0f) region.right else layout.widthDp
		projection.y1Dp?.let { y1 ->
			drawRect(ink.primary.copy(alpha = .1f), at(x0, projection.yDp), Size(px(x1 - x0), px(y1 - projection.yDp)))
		}
		drawLine(
			ink.primary,
			at(x0, projection.yDp),
			at(x1, projection.yDp),
			strokeWidth = px(1.2f),
			pathEffect = dotted(),
		)
	}
}
