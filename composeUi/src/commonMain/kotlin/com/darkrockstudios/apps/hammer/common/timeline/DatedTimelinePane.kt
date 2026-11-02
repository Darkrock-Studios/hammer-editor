package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
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

private const val FALLBACK_WIDTH_DP = 840f

/** The drawing of a DATED timeline's [rows], laid out to the width it is given. */
@Composable
internal fun DatedTimelinePane(
	state: TimeLineOverview.State,
	rows: List<TimelineRow>,
	onTap: (HitTarget) -> Unit,
	modifier: Modifier = Modifier,
) {
	val words = rememberStoryDateWords()
	val measurer = rememberTimelineTextMeasurer()
	val colors = LocalHammerColors.current
	val phone = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	val calendar = state.calendar ?: CalendarDef()
	val laneDescriptions = state.lanes.associate { it.lane to Res.string.timeline_lane_filter_description.get(it.lane) }
	val describeLane: (String) -> String = { lane -> laneDescriptions[lane] ?: lane }

	BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(TIMELINE_DRAWING_TAG)) {
		val widthDp = if (maxWidth.value.isFinite()) (maxWidth - MpScrollBarGutter).value.coerceAtLeast(0f) else FALLBACK_WIDTH_DP
		val layout = remember(rows, state.lanes, state.laneFilter, state.view, calendar, widthDp, phone, words, measurer) {
			val input = toLayoutInput(
				rows = rows,
				lanes = state.lanes,
				laneFilter = state.laneFilter,
				calendar = calendar,
				view = state.view,
				widthDp = widthDp,
				phone = phone,
				words = words,
			)
			TimelineLayoutEngine.layout(input, measurer)
		}
		// The layout numbers only the lanes it draws; a lane's colour follows its place in the full list.
		val paletteIndex = remember(layout, state.lanes) {
			layout.lanes.map { lane -> state.lanes.indexOfFirst { it.lane == lane.name }.takeIf { it >= 0 } ?: lane.index }
		}
		val titles = remember(rows) { rows.associate { it.event.id to it.title } }

		TimelineDrawingPane(
			layout = layout,
			laneColor = { index -> colors.laneColor(paletteIndex.getOrElse(index) { index }) },
			selectedId = null,
			onTap = onTap,
			titles = titles,
			laneHeaderDescription = describeLane,
			measurer = measurer,
			modifier = Modifier.fillMaxSize(),
		)
	}
}
