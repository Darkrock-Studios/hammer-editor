import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.timeline.MOVE_TO_TIMELINE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_VIEW_ALSO_ON_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_VIEW_MOVE_TAG
import com.darkrockstudios.apps.hammer.common.timeline.ViewTimeLineEventUi
import com.darkrockstudios.apps.hammer.common.timeline.timelineAlsoOnChipTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ViewTimeLineEventMoveUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val scratch = TimelineDef(id = "scratch", name = "Scratch")
	private val backstory = ViewTimeLineEvent.OtherPlacement(timelineId = "backstory", name = "Before the evening", eventId = 9)

	private class FakeView(initial: ViewTimeLineEvent.State) : ViewTimeLineEvent {
		private val _state = MutableValue(initial)
		override val eventId: Int = initial.event?.id ?: 0
		override val state: Value<ViewTimeLineEvent.State> = _state
		override val dateText: Value<String> = MutableValue("")
		override val contentText: Value<String> = MutableValue("")

		val shown = mutableListOf<ViewTimeLineEvent.OtherPlacement>()
		val moves = mutableListOf<String>()

		override fun startMove() {
			_state.value = _state.value.copy(confirmMove = true)
		}

		override fun cancelMove() {
			_state.value = _state.value.copy(confirmMove = false)
		}

		override suspend fun moveToTimeline(target: String, start: StoryDate?, end: StoryDate?): TimeLineEventError {
			moves += target
			return TimeLineEventError.NONE
		}

		override fun showPlacement(placement: ViewTimeLineEvent.OtherPlacement) {
			shown += placement
		}

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
		override fun isEditingAndDirty() = false
		override fun discardEdit() {}
		override fun beginEdit() {}
		override fun confirmClose() {}
		override fun cancelClose() {}
		override fun closeEvent() {}
		override suspend fun pickImageForText(): ImageDef? = null
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	private fun placement(
		alsoOn: List<ViewTimeLineEvent.OtherPlacement> = emptyList(),
		moveTargets: List<TimelineDef> = emptyList(),
	) = FakeView(
		ViewTimeLineEvent.State(
			event = TimeLineEvent(id = 4, order = 0, kind = TimelineKind.SCENE, subject = 11, cachedTitle = "Dinner"),
			kind = TimelineKind.SCENE,
			displayTitle = "Dinner",
			subject = SubjectSummary.Missing,
			alsoOn = alsoOn,
			moveTargets = moveTargets,
		)
	)

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun show(component: ViewTimeLineEvent) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.requiredSize(900.dp, 800.dp)) {
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
		compose.waitForIdle()
	}

	@Test
	fun `A placement on other timelines shows them under Also on, and a chip opens the placement there`() {
		val component = placement(alsoOn = listOf(TimelineDef.DEFAULT.let { ViewTimeLineEvent.OtherPlacement(it.id, it.name, 2) }, backstory))
		show(component)

		compose.onNodeWithTag(TIME_LINE_VIEW_ALSO_ON_TAG).assertIsDisplayed()
		compose.onNodeWithText("ALSO ON").assertIsDisplayed()
		compose.onNodeWithText("Main").assertIsDisplayed()

		compose.onNodeWithText(backstory.name).performClick()
		compose.waitForIdle()

		assertEquals(listOf(backstory), component.shown)
	}

	@Test
	fun `A placement on one timeline only has no Also on row`() {
		show(placement())

		compose.onNodeWithTag(TIME_LINE_VIEW_ALSO_ON_TAG).assertDoesNotExist()
		compose.onNodeWithTag(timelineAlsoOnChipTag(backstory.timelineId)).assertDoesNotExist()
	}

	@Test
	fun `Move to timeline opens the move dialog, which moves to the picked target`() {
		val component = placement(moveTargets = listOf(scratch))
		show(component)

		compose.onNodeWithTag(TIME_LINE_VIEW_MOVE_TAG).performClick()
		compose.waitForIdle()
		compose.onNodeWithText("Move to another timeline").assertIsDisplayed()

		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).performClick()
		compose.waitUntil { component.moves.isNotEmpty() }

		assertEquals(listOf("scratch"), component.moves)
	}

	@Test
	fun `With nowhere to move to, there is no Move action`() {
		show(placement())

		compose.onNodeWithTag(TIME_LINE_VIEW_MOVE_TAG).assertDoesNotExist()
	}
}
