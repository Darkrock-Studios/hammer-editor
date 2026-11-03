package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.basicPreview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineOverviewUi
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView

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

/** A phone's overview of a dated timeline: filters, then the stacked pickers, then the strip. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoneDatedOverview(dark: Boolean) {
	val scope = rememberCoroutineScope()
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, dark) {
			Surface {
				SharedTransitionLayout {
					AnimatedVisibility(visible = true) {
						TimeLineOverviewUi(
							component = PreviewOverview(
								TimeLineOverview.State(
									rows = previewDatedRows,
									mode = TimelineMode.DATED,
									calendar = previewCalendar,
									undatedIds = listOf(4),
									lanes = previewLaneCounts,
									view = TimelineView.TO_SCALE,
									storyFrameAvailable = true,
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
}

@Preview(widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP)
@Composable
fun ScreenTimeLineOverviewUiPhoneDatedPreview() = PhoneDatedOverview(dark = false)

@Preview(widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP)
@Composable
fun ScreenTimeLineOverviewUiPhoneDatedDarkPreview() = PhoneDatedOverview(dark = true)

private const val PHONE_WIDTH_DP = 360
private const val PHONE_HEIGHT_DP = 760

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
	override fun setView(view: TimelineView) {}
	override fun canFrame(eventId: Int): Boolean = false
	override fun setFrame(frame: TimelineFrame) {}
	override suspend fun preview(eventId: Int): EventPreview? =
		state.value.rows?.firstOrNull { it.event.id == eventId }?.basicPreview()
}