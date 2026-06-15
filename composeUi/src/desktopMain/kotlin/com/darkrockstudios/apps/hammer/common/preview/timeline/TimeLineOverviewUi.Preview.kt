package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
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

private val events = listOf(
	TimeLineEvent(
		id = 1,
		order = 0,
		date = "Spring, Year One",
		content = "The keeper arrives at the lighthouse and meets the villagers.",
		tags = setOf("act-one"),
	),
	TimeLineEvent(
		id = 2,
		order = 1,
		date = "Midsummer",
		content = "The lamp goes dark for the first time and a ship runs aground.",
		tags = setOf("act-one", "turning-point"),
	),
	TimeLineEvent(
		id = 3,
		order = 2,
		date = null,
		content = "An undated rumour spreads through the harbour town.",
	),
)

private val component = object : TimeLineOverview {
	override val state: Value<TimeLineOverview.State> = MutableValue(
		TimeLineOverview.State(
			timeLine = TimeLineContainer(events = events),
		)
	)
	override val rankedTags: Value<List<TagCount>> = MutableValue(
		listOf(
			TagCount("act-one", 2),
			TagCount("turning-point", 1),
		)
	)

	override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = false
}
