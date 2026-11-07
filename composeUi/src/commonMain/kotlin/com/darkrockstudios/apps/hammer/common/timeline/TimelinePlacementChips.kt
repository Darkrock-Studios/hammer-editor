package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_EVENT_GLYPH
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberStoryDateWords
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.timeline_deleted_bucket

const val TIMELINE_PLACEMENTS_TAG = "timeline-placements"

fun timelinePlacementChipTag(eventId: Int) = "timeline-placement-$eventId"

/**
 * A chip per placement of a scene or entry, naming its timeline and its when as the timeline shows
 * it; clicking one opens the placement there. Draws nothing when [placements] is empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimelinePlacementChips(
	placements: List<TimelinePlacementLink>,
	onShow: (TimelinePlacementLink) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (placements.isEmpty()) return
	val words = rememberStoryDateWords()
	val deleted = Res.string.timeline_deleted_bucket.get()
	FlowRow(
		modifier = modifier
			.fillMaxWidth()
			.testTag(TIMELINE_PLACEMENTS_TAG),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		placements.forEach { placement ->
			HdReferenceChip(
				glyph = HD_EVENT_GLYPH,
				swatchColor = MaterialTheme.colorScheme.onSurfaceVariant,
				glyphColor = MaterialTheme.colorScheme.surface,
				name = placement.chipName(deleted, words),
				onClick = { onShow(placement) },
				onAction = null,
				actionContentDescription = "",
				modifier = Modifier.testTag(timelinePlacementChipTag(placement.eventId)),
			)
		}
	}
}

private fun TimelinePlacementLink.chipName(deleted: String, words: StoryDateWords): String {
	val start = start
	val whenText = if (start != null) formatStoryDateRange(calendar ?: CalendarDef(), start, end, words) else label
	return listOfNotNull(timelineName ?: deleted, whenText).joinToString(" · ")
}
