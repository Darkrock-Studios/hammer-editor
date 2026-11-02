package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineOverviewUi

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ScreenTimeLineOverviewUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				TimeLineOverviewUi(
					component = component,
					scope = scope,
					showCreate = {},
					viewEvent = {},
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedVisibility,
				)
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenTimeLineOverviewUiTabletPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		TabletPreviewSurface {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					TimeLineOverviewUi(
						component = component,
						scope = scope,
						showCreate = {},
						viewEvent = {},
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenTimeLineOverviewUiDatedPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		TabletPreviewSurface {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					TimeLineOverviewUi(
						component = PreviewOverview(
							TimeLineOverview.State(
								rows = previewRows,
								mode = TimelineMode.DATED,
								calendar = CalendarDef(epochName = "The Landing"),
								undatedIds = listOf(4),
								lanes = previewLaneCounts,
							)
						),
						scope = scope,
						showCreate = {},
						viewEvent = {},
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
	}
}

private val component = PreviewOverview(TimeLineOverview.State(rows = previewRows, lanes = previewLaneCounts))

private class PreviewOverview(initial: TimeLineOverview.State) : TimeLineOverview {
	override val state: Value<TimeLineOverview.State> = MutableValue(initial)
	override val rankedTags: Value<List<TagCount>> = MutableValue(
		listOf(
			TagCount("act-one", 1),
			TagCount("turning-point", 1),
		)
	)

	override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = false
	override fun setLaneFilter(lanes: Set<String>) {}
	override fun toggleLane(lane: String) {}
}