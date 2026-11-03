package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.basicPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.toLayoutInput
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_DRAWING_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawingPane
import com.darkrockstudios.apps.hammer.common.timeline.drawing.rememberTimelineTextMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.timeline_lane_filter_description
import kotlinx.coroutines.launch
import kotlin.math.floor

private const val FALLBACK_WIDTH_DP = 840f
private const val WIDTH_STEP_DP = 8f

/**
 * The drawing of a DATED timeline's [rows] in the state's frame, laid out to the width it is given.
 * A new frame starts scrolled to the top. [bottomPadding] keeps the last rows clear of an overlay.
 * [onContentHeight] hears the drawing's full height, padding included, and [onSpans] the events it
 * draws as spans. Where a pointer hovers, an event's hover card shows what [preview] reads for it.
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
	onOpen: ((Int) -> Unit)? = null,
	bottomPadding: Dp = 0.dp,
	onContentHeight: (Dp) -> Unit = {},
	preview: (suspend (eventId: Int) -> EventPreview?)? = null,
	onSpans: (Set<Int>) -> Unit = {},
) {
	val words = rememberStoryDateWords()
	val colors = LocalHammerColors.current
	val phone = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	val measurer = rememberTimelineTextMeasurer(phone)
	val calendar = state.calendar ?: CalendarDef()
	val laneDescriptions = state.lanes.associate { it.lane to Res.string.timeline_lane_filter_description.get(it.lane) }
	val describeLane: (String) -> String = { lane -> laneDescriptions[lane] ?: lane }

	val scrollState = rememberScrollState()
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
		val layout = remember(
			rows,
			state.lanes,
			state.laneFilter,
			state.view,
			state.frame,
			state.selectedId,
			calendar,
			steppedWidthDp,
			phone,
			words,
			measurer,
		) {
			val input = toLayoutInput(
				rows = rows,
				lanes = state.lanes,
				laneFilter = state.laneFilter,
				calendar = calendar,
				view = state.view,
				widthDp = steppedWidthDp,
				phone = phone,
				frame = state.frame,
				selectedId = state.selectedId,
				words = words,
			)
			TimelineLayoutEngine.layout(input.copy(widthDp = maxOf(steppedWidthDp, TimelineLayoutEngine.minimumWidthDp(input, measurer))), measurer)
		}
		// The layout numbers only the lanes it draws; a lane's colour follows its place in the full list.
		val paletteIndex = remember(layout, state.lanes) {
			layout.lanes.map { lane -> state.lanes.indexOfFirst { it.lane == lane.name }.takeIf { it >= 0 } ?: lane.index }
		}
		val titles = remember(rows) { rows.associate { it.event.id to it.title } }
		val contentHeight = layout.heightDp.dp + bottomPadding
		val reportContentHeight by rememberUpdatedState(onContentHeight)
		LaunchedEffect(contentHeight) { reportContentHeight(contentHeight) }
		val reportSpans by rememberUpdatedState(onSpans)
		LaunchedEffect(layout) {
			reportSpans(
				buildSet {
					layout.bands.forEach { add(it.eventId) }
					layout.bars.forEach { add(it.eventId) }
					layout.ledgerRows.forEach { if (it.anchorY1Dp != null) add(it.eventId) }
				}
			)
		}

		// Until its preview arrives a card shows what the row already knows.
		val rowsById = remember(rows) { rows.associateBy { it.event.id } }
		val previews = remember(rows) { mutableStateMapOf<Int, EventPreview>() }
		val previewScope = rememberCoroutineScope()
		val readPreview by rememberUpdatedState(preview)
		val hoverCard: (@Composable (Int) -> Unit)? = if (preview != null) {
			{ id ->
				(previews[id] ?: rowsById[id]?.basicPreview())?.let { EventPreviewCard(it, compact = true) }
			}
		} else {
			null
		}

		TimelineDrawingPane(
			layout = layout,
			laneColor = { index -> colors.laneColor(paletteIndex.getOrElse(index) { index }) },
			selectedId = state.selectedId,
			onTap = onTap,
			onOpen = onOpen,
			titles = titles,
			laneHeaderDescription = describeLane,
			measurer = measurer,
			modifier = Modifier.fillMaxSize(),
			contentWidth = layout.widthDp.takeIf { it > widthDp }?.dp,
			scrollState = scrollState,
			bottomPadding = bottomPadding,
			hoverCard = hoverCard,
			onHoverStart = { id ->
				previewScope.launch { readPreview?.invoke(id)?.let { previews[id] = it } }
			},
		)
	}
}
