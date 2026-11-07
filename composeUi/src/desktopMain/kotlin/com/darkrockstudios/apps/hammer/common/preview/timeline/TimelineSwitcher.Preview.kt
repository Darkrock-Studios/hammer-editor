package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherActions
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherDialogs
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherHeader
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherMenuItems

/** The switcher open on Before the evening, with two events in the deleted-timeline bucket. */
@Preview
@Composable
fun TimelineSwitcherMenuPreview() {
	AppTheme(globalSettingsPreview) {
		Surface {
			Column(
				modifier = Modifier
					.padding(16.dp)
					.width(300.dp)
					.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
					.padding(vertical = 8.dp),
			) {
				TimelineSwitcherMenuItems(
					state = TimeLineOverview.State(
						timelines = previewThreeTimelines,
						selection = TimelineSelection.Def("backstory"),
						orphanCount = 2,
					),
					actions = TimelineSwitcherActions.None,
					close = {},
				)
			}
		}
	}
}

/** The masthead with main alone, then with three timelines on each one in turn. */
@Preview(widthDp = 720)
@Composable
fun TimelineSwitcherHeaderPreview() {
	AppTheme(globalSettingsPreview) {
		Surface {
			Column(modifier = Modifier.padding(16.dp)) {
				TimelineSwitcherHeader(
					state = TimeLineOverview.State(timelines = previewMainOnly),
					actions = TimelineSwitcherActions.None,
				)
				previewThreeTimelines.forEach { summary ->
					TimelineSwitcherHeader(
						state = TimeLineOverview.State(
							timelines = previewThreeTimelines,
							selection = TimelineSelection.Def(summary.def.id),
						),
						actions = TimelineSwitcherActions.None,
					)
				}
				TimelineSwitcherHeader(
					state = TimeLineOverview.State(
						timelines = previewThreeTimelines,
						selection = TimelineSelection.Orphans,
						orphanCount = 2,
					),
					actions = TimelineSwitcherActions.None,
				)
			}
		}
	}
}

@Composable
private fun SwitcherDialog(switcher: TimelineSwitcher) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			TimelineSwitcherDialogs(
				switcher = switcher,
				onCreate = { _, _, _ -> },
				onRename = {},
				onConfirmDelete = {},
				onDismiss = {},
			)
		}
	}
}

@Preview
@Composable
fun TimelineSwitcherCreateDialogPreview() = SwitcherDialog(
	TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create(name = "Before the evening", mode = TimelineMode.DATED))
)

@Preview
@Composable
fun TimelineSwitcherRenameRefusedPreview() = SwitcherDialog(
	TimelineSwitcher(
		dialog = TimelineSwitcher.Dialog.Rename(timelineId = "backstory", name = "Main"),
		error = TimeLineEventError.TIMELINE_NAME_INVALID,
	)
)

@Preview
@Composable
fun TimelineSwitcherDeleteDialogPreview() = SwitcherDialog(
	TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Delete(timelineId = "backstory", name = "Before the evening", eventCount = 13))
)
