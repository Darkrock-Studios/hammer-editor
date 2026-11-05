package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.timeline_lane_filter_description

/** One `HdTagChip` per lane, its swatch in the lane's colour: `▪ Alice · 4`. */
@Composable
internal fun LaneFilterChips(
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
