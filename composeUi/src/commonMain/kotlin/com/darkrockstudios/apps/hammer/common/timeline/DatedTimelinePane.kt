package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.basicPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.toLayoutInput
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.timeline.drawing.LocalTimelineLayoutContext
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_DRAWING_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawingPane
import com.darkrockstudios.apps.hammer.common.timeline.drawing.eventId
import com.darkrockstudios.apps.hammer.common.timeline.drawing.rememberTimelineLayoutMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.drawing.rememberTimelineTextMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayout
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.timeline_lane_filter_description
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

private const val FALLBACK_WIDTH_DP = 840f
private const val WIDTH_STEP_DP = 8f

/**
 * The drawing of a DATED timeline's [rows] in the state's frame, laid out to the width it is given.
 * A new frame starts scrolled to the top, and [topPadding] lets the drawing start under an overlay. An
 * event's preview card shows its entry in [previews], asked for through [requestPreview], where a
 * pointer hovers or a long press holds; the event a long press holds is projected across the lanes
 * until it is released. Without [previews] no card is shown.
 * [highlighted] events are ringed as search matches, and the drawing scrolls [focusedId] into view.
 *
 * The layout is computed on [LocalTimelineLayoutContext]. Until the first one is ready the pane is
 * empty; after that it keeps showing the last layout while the next is computed.
 *
 * On a phone (Compact width) lanes are 22dp rails with every label in the ledger. Only the lanes in
 * the lane filter are drawn (all of them while it is empty), so the lane chips are how a writer picks
 * the rails on a crowded timeline; rails that still do not fit scroll sideways.
 */
@Composable
internal fun DatedTimelinePane(
	state: TimeLineOverview.State,
	rows: List<TimelineRow>,
	onTap: (HitTarget) -> Unit,
	modifier: Modifier = Modifier,
	eventSource: (@Composable BoxScope.(eventId: Int) -> Unit)? = null,
	scrollState: ScrollState = rememberScrollState(),
	topPadding: Dp = 0.dp,
	highlighted: Set<Int> = emptySet(),
	focusedId: Int? = null,
	previews: Value<Map<Int, EventPreview>>? = null,
	requestPreview: (eventId: Int) -> Unit = {},
) {
	val words = rememberStoryDateWords()
	val colors = LocalHammerColors.current
	val phone = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	val measurer = rememberTimelineTextMeasurer(phone)
	val layoutMeasurer = rememberTimelineLayoutMeasurer(phone)
	val layoutContext = LocalTimelineLayoutContext.current
	val calendar = state.calendar ?: CalendarDef()
	val laneDescriptions = state.lanes.associate { it.lane to Res.string.timeline_lane_filter_description.get(it.lane) }
	val describeLane = remember(laneDescriptions) { { lane: String -> laneDescriptions[lane] ?: lane } }

	var shownFrame by remember { mutableStateOf(state.frame) }
	LaunchedEffect(state.frame) {
		if (state.frame != shownFrame) {
			shownFrame = state.frame
			scrollState.scrollTo(0)
		}
	}

	BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(TIMELINE_DRAWING_TAG)) {
		val widthDp = if (maxWidth.value.isFinite()) (maxWidth - MpScrollBarGutter).value.coerceAtLeast(0f) else FALLBACK_WIDTH_DP
		// Stepped so that resizing the window does not lay the timeline out again on every frame.
		val steppedWidthDp = floor(widthDp / WIDTH_STEP_DP) * WIDTH_STEP_DP
		val lanes = state.lanes
		val laneFilter = state.laneFilter
		val view = state.view
		val frame = state.frame
		val entryMark = TimelineKind.ENTRY.hdMark()
		// A change of input cancels the layout in progress; a cancelled layout is never shown.
		val laidOut by produceState<TimelineLayout?>(
			null,
			rows,
			lanes,
			laneFilter,
			view,
			frame,
			calendar,
			steppedWidthDp,
			phone,
			words,
			layoutMeasurer,
			layoutContext,
		) {
			value = withContext(layoutContext) {
				val input = toLayoutInput(
					rows = rows,
					lanes = lanes,
					laneFilter = laneFilter,
					calendar = calendar,
					view = view,
					widthDp = steppedWidthDp,
					phone = phone,
					frame = frame,
					words = words,
					entryMark = entryMark,
				)
				val width = maxOf(steppedWidthDp, TimelineLayoutEngine.minimumWidthDp(input, layoutMeasurer))
				TimelineLayoutEngine.layout(input.copy(widthDp = width), layoutMeasurer)
			}
		}
		// Brings a newly focused match a third of the way down the pane, once there is a layout to find it in.
		val density = LocalDensity.current
		val viewportPx = with(density) { maxHeight.toPx() }
		val currentLayout by rememberUpdatedState(laidOut)
		LaunchedEffect(focusedId, laidOut == null) {
			val id = focusedId ?: return@LaunchedEffect
			val rect = currentLayout?.hitRegions?.firstOrNull { it.target.eventId == id }?.rect ?: return@LaunchedEffect
			val top = with(density) { (rect.y.dp + topPadding).toPx() }
			scrollState.animateScrollTo((top - viewportPx / 3f).roundToInt().coerceIn(0, scrollState.maxValue))
		}
		val layout = laidOut ?: return@BoxWithConstraints

		// The layout numbers only the lanes it draws; a lane's colour follows its place in the full list.
		val paletteIndex = remember(layout, state.lanes) {
			layout.lanes.map { lane -> state.lanes.indexOfFirst { it.lane == lane.name }.takeIf { it >= 0 } ?: lane.index }
		}
		val titles = remember(rows) { rows.associate { it.event.id to it.title } }

		// Until its preview arrives a card shows what the row already knows.
		val rowsById = remember(rows) { rows.associateBy { it.event.id } }
		val currentRowsById by rememberUpdatedState(rowsById)
		// Each card observes the previews and rows itself, so the lambda stays the same across
		// timeline updates and only the cards recompose.
		val hoverCard: (@Composable (Int) -> Unit)? = remember(previews) {
			previews?.let { loaded ->
				val card: @Composable (Int) -> Unit = { id ->
					val shown by loaded.subscribeAsState()
					(shown[id] ?: currentRowsById[id]?.basicPreview())?.let { EventPreviewCard(it) }
				}
				card
			}
		}

		TimelineDrawingPane(
			layout = layout,
			laneColor = { index -> colors.laneColor(paletteIndex.getOrElse(index) { index }) },
			onTap = onTap,
			eventSource = eventSource,
			titles = titles,
			laneHeaderDescription = describeLane,
			measurer = measurer,
			modifier = Modifier.fillMaxSize(),
			contentWidth = layout.widthDp.takeIf { it > widthDp }?.dp,
			scrollState = scrollState,
			topPadding = topPadding,
			highlighted = highlighted,
			focusedId = focusedId,
			hoverCard = hoverCard,
			onHoverStart = requestPreview,
		)
	}
}
