import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.timeline.EventBodyMinHeight
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_REFERENCES_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_VIEW_BODY_TAG
import com.darkrockstudios.apps.hammer.common.timeline.ViewTimeLineEventUi
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WINDOW_TAG = "test-window"

class ViewTimeLineEventEditUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private class FakeView(initial: ViewTimeLineEvent.State) : ViewTimeLineEvent {
		override val eventId: Int = initial.event?.id ?: 0
		override val state: Value<ViewTimeLineEvent.State> = MutableValue(initial)
		override val dateText: Value<String> = MutableValue("")
		override val contentText: Value<String> = MutableValue("")

		override fun onEventTextChanged(text: String) {}
		override fun onDateTextChanged(text: String) {}
		override fun onTagsChanged(newTags: Set<String>) {}
		override fun onTitleChanged(text: String) {}
		override fun onLaneChanged(text: String) {}
		override fun onMilestoneChanged(milestone: Boolean) {}
		override fun onStartChanged(start: StoryDate?) {}
		override fun onEndChanged(end: StoryDate?) {}
		override fun addReference(item: CatalogItem) {}
		override fun removeReference(id: Int) {}
		override fun suggestLanes(prefix: String): List<String> = emptyList()
		override suspend fun removeTag(tag: String) {}
		override fun showGlobalSearchForTag(tag: String) {}
		override suspend fun saveEdit() = TimeLineEventError.NONE
		override fun startDeleteEvent() {}
		override fun endDeleteEvent() {}
		override suspend fun deleteEvent() {}
		override fun openSubject() {}
		override fun openReference(item: CatalogItem) {}
		override fun confirmDiscard() {}
		override fun cancelDiscard() {}
		override fun startMove() {}
		override fun cancelMove() {}
		override suspend fun moveToTimeline(target: String, start: StoryDate?, end: StoryDate?): TimeLineEventError =
			TimeLineEventError.NONE

		override fun showPlacement(placement: ViewTimeLineEvent.OtherPlacement) {}
		override fun isEditingAndDirty() = false
		override fun discardEdit() {}
		override fun beginEdit() {}
		override fun confirmClose() {}
		override fun cancelClose() {}
		override fun closeEvent() {}
		override suspend fun pickImageForText(): ImageDef? = null
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	private fun editingDatedEvent() = FakeView(
		ViewTimeLineEvent.State(
			event = TimeLineEvent(id = 3, order = 0, title = "The trial"),
			isEditing = true,
			title = "The trial",
			mode = TimelineMode.DATED,
			calendar = CalendarDef(epochName = "Landing"),
			lanes = listOf("Alice", "Queen"),
		)
	)

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun show(component: ViewTimeLineEvent, size: DpSize) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.requiredSize(size).testTag(WINDOW_TAG)) {
					SharedTransitionLayout {
						AnimatedVisibility(visible = true) {
							ViewTimeLineEventUi(
								component = component,
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
	}

	private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

	@Test
	fun `Editing on a phone keeps the body's height and scrolls the fields above it`() {
		show(editingDatedEvent(), DpSize(360.dp, 640.dp))

		val window = bounds(WINDOW_TAG)
		val body = bounds(TIME_LINE_VIEW_BODY_TAG)
		assertTrue(body.height >= EventBodyMinHeight, "body is ${body.height}")
		assertTrue(body.bottom <= window.bottom, "body ends at ${body.bottom}, window at ${window.bottom}")
		compose.onNodeWithText("Save", ignoreCase = true).assertIsDisplayed()

		// Labels, not the fields: a single line text field scrolls itself, so performScrollTo stops there.
		compose.onNodeWithTag(TIME_LINE_REFERENCES_TAG).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Tags", ignoreCase = true).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Start", ignoreCase = true).performScrollTo().assertIsDisplayed()

		assertEquals(body, bounds(TIME_LINE_VIEW_BODY_TAG))
	}

	@Test
	fun `Editing on a window too short for the body's minimum still reaches every field`() {
		show(editingDatedEvent(), DpSize(360.dp, 400.dp))

		val window = bounds(WINDOW_TAG)
		val body = bounds(TIME_LINE_VIEW_BODY_TAG)
		assertTrue(body.height >= EventBodyMinHeight / 2, "body is ${body.height}")
		assertTrue(body.bottom <= window.bottom, "body ends at ${body.bottom}, window at ${window.bottom}")
		compose.onNodeWithText("Save", ignoreCase = true).assertIsDisplayed()

		compose.onNodeWithTag(TIME_LINE_REFERENCES_TAG).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Start", ignoreCase = true).performScrollTo().assertIsDisplayed()
	}
}
