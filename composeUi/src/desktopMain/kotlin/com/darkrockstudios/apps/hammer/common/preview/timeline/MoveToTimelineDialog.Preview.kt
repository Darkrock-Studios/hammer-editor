package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.MoveToTimelineDraft
import com.darkrockstudios.apps.hammer.common.timeline.MoveToTimelineFields
import com.darkrockstudios.apps.hammer.timeline_move_title
import com.darkrockstudios.apps.hammer.timeline_switcher_close

/** An event on main, which can go to the two other timelines. */
internal val previewMoveTargets = listOf(
	TimelineDef(
		id = "backstory",
		name = "Before the evening",
		mode = TimelineMode.DATED,
		calendar = CalendarDef(CalendarPreset.GREGORIAN),
	),
	TimelineDef(id = "scratch", name = "Scratch: what the keeper remembers of the wreck"),
)

/** The move dialog as it opens, on the first target. */
@Preview
@Composable
fun MoveToTimelineDialogPreview() {
	MovePreview(remember { MoveToTimelineDraft(previewMoveTargets.first().id) })
}

/** The Gregorian target refused the event's story dates, so the dialog asks for one there. */
@Preview
@Composable
fun MoveToTimelineDialogNeedsDatePreview() {
	MovePreview(remember { MoveToTimelineDraft(previewMoveTargets.first().id, dateNeeded = true) })
}

@Composable
private fun MovePreview(draft: MoveToTimelineDraft) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Surface {
				HdHairlineDialogShell(
					title = Res.string.timeline_move_title.get(),
					onClose = {},
					closeContentDescription = Res.string.timeline_switcher_close.get(),
					modifier = Modifier.padding(16.dp),
				) {
					MoveToTimelineFields(
						targets = previewMoveTargets,
						draft = draft,
						onMove = { _, _, _ -> TimeLineEventError.NONE },
						onCancel = {},
					)
				}
			}
		}
	}
}
