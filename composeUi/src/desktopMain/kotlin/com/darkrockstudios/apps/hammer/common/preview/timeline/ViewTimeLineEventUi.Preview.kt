package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.timeline.ViewTimeLineEventUi

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ScreenViewTimeLineEventUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				ViewTimeLineEventUi(
					component = component,
					modifier = Modifier,
					scope = scope,
					rootSnackbar = rememberRootSnackbarHostState(),
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
fun ScreenViewTimeLineEventUiTabletPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		TabletPreviewSurface {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					ViewTimeLineEventUi(
						component = component,
						modifier = Modifier,
						scope = scope,
						rootSnackbar = rememberRootSnackbarHostState(),
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
	}
}

private val event = TimeLineEvent(
	id = 1,
	order = 1,
	date = "Midsummer",
	content = "The lamp goes dark for the first time and a ship runs aground on the rocks below the cliff.",
	tags = setOf("act-one", "turning-point"),
)

private val component = object : ViewTimeLineEvent {
	override val eventId: Int = event.id
	override val state: Value<ViewTimeLineEvent.State> = MutableValue(
		ViewTimeLineEvent.State(
			event = event,
			tags = event.tags,
		)
	)
	override val dateText: Value<String> = MutableValue(event.date.orEmpty())
	override val contentText: Value<String> = MutableValue(event.content)

	override fun onEventTextChanged(text: String) {}
	override fun onDateTextChanged(text: String) {}
	override fun onTagsChanged(newTags: Set<String>) {}
	override suspend fun removeTag(tag: String) {}
	override fun showGlobalSearchForTag(tag: String) {}
	override suspend fun storeEvent(event: TimeLineEvent): Boolean = true
	override fun startDeleteEvent() {}
	override fun endDeleteEvent() {}
	override suspend fun deleteEvent() {}
	override fun confirmDiscard() {}
	override fun cancelDiscard() {}
	override fun isEditingAndDirty(): Boolean = false
	override fun discardEdit() {}
	override fun beginEdit() {}
	override fun confirmClose() {}
	override fun cancelClose() {}
	override fun closeEvent() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
