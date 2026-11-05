import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.darkrockstudios.apps.hammer.common.timeline.EventBodyMinHeight
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_BODY_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_MILESTONE_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_REFERENCES_TAG
import kotlin.test.assertTrue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.timeline.CreateTimeLineEventUi
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_CONTENT_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_ADD_END_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_DATE_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_END_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_START_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_LANE_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_SUBJECT_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_TAGS_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_CREATE_TITLE_TAG
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val WINDOW_TAG = "test-window"

class CreateTimeLineEventUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val projectDef = fakeProjectDef()

	private class FakeCreate(initial: CreateTimeLineEvent.State) : CreateTimeLineEvent {
		private val _state = MutableValue(initial)
		override val state: Value<CreateTimeLineEvent.State> = _state
		override val contentText: Value<String> = MutableValue("")

		override suspend fun create(label: String?, content: String, tags: Set<String>) = TimeLineEventError.NONE

		override fun setKind(kind: TimelineKind) {
			_state.value = _state.value.copy(kind = kind, subject = null)
		}

		override fun setSubject(item: CatalogItem?) {
			_state.value = _state.value.copy(subject = item)
		}

		override fun setLane(lane: String) {
			_state.value = _state.value.copy(lane = lane)
		}

		override fun setMilestone(milestone: Boolean) {}
		override fun setTitle(title: String) {}
		override fun setStart(start: StoryDate?) {
			_state.value = _state.value.copy(start = start)
		}

		override fun setEnd(end: StoryDate?) {
			_state.value = _state.value.copy(end = end)
		}

		override fun addReference(item: CatalogItem) {}
		override fun removeReference(id: Int) {}
		override fun suggestLanes(prefix: String): List<String> =
			_state.value.lanes.filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }

		override fun closeCreation() {}
		override fun discardAndClose() {}
		override fun confirmDiscard() {}
		override fun cancelDiscard() {}
		override fun onContentChanged(newText: String) {}
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

	private fun show(component: CreateTimeLineEvent, size: DpSize? = null) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = if (size != null) Modifier.requiredSize(size).testTag(WINDOW_TAG) else Modifier) {
					CreateTimeLineEventUi(
						component = component,
						scope = scope,
						modifier = Modifier,
						rootSnackbar = rememberRootSnackbarHostState(),
					)
				}
			}
		}
	}

	private fun datedEvent() = FakeCreate(
		CreateTimeLineEvent.State(
			projectDef = projectDef,
			mode = TimelineMode.DATED,
			calendar = CalendarDef(epochName = "Landing"),
			lanes = listOf("Alice", "Queen"),
		)
	)

	private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

	/** The body keeps its minimum, Create stays inside the window, and the fields scroll into view. */
	private fun assertPhoneLayout() {
		val window = bounds(WINDOW_TAG)
		val body = bounds(TIME_LINE_CREATE_BODY_TAG)
		assertTrue(body.height >= EventBodyMinHeight, "body is ${body.height}")
		assertTrue(body.bottom <= window.bottom, "body ends at ${body.bottom}, window at ${window.bottom}")

		val create = compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		assertTrue(create.top >= body.bottom, "Create starts at ${create.top}, above the body's end ${body.bottom}")
		assertTrue(create.bottom <= window.bottom, "Create ends at ${create.bottom}, window at ${window.bottom}")

		// Labels, not the fields: a single line text field scrolls itself, so performScrollTo stops there.
		compose.onNodeWithTag(TIME_LINE_REFERENCES_TAG).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Tags", ignoreCase = true).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Start", ignoreCase = true).performScrollTo().assertIsDisplayed()

		assertEquals(body, bounds(TIME_LINE_CREATE_BODY_TAG))
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsDisplayed()
	}

	@Test
	fun `On a phone the body keeps its height and the fields scroll above it`() {
		show(datedEvent(), DpSize(360.dp, 640.dp))
		assertPhoneLayout()
	}

	@Test
	fun `With the keyboard up on a phone the body keeps its height and Create stays in the window`() {
		show(datedEvent(), DpSize(360.dp, 400.dp))
		assertPhoneLayout()
	}

	@Test
	fun `A tall window shows every field and gives the body the rest`() {
		show(datedEvent(), DpSize(900.dp, 1100.dp))

		val references = compose.onNodeWithTag(TIME_LINE_REFERENCES_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		val body = bounds(TIME_LINE_CREATE_BODY_TAG)
		val create = bounds(TIME_LINE_CREATE_CONFIRM_TAG)
		assertTrue(body.height > EventBodyMinHeight, "body is ${body.height}")
		assertTrue(references.bottom <= body.top, "references end at ${references.bottom}, body starts at ${body.top}")
		// The footer's padding and rule are all that sit between the body and Create.
		assertTrue(create.top - body.bottom < 48.dp, "gap of ${create.top - body.bottom} between body and Create")
	}

	@Test
	fun `A placement on a short window scrolls its fields and keeps Create in the window`() {
		val component = datedEvent()
		component.setKind(TimelineKind.SCENE)
		show(component, DpSize(360.dp, 400.dp))

		val window = bounds(WINDOW_TAG)
		val create = compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		assertTrue(create.bottom <= window.bottom, "Create ends at ${create.bottom}, window at ${window.bottom}")
		compose.onNodeWithTag(TIME_LINE_CREATE_MILESTONE_TAG).performScrollTo().assertIsDisplayed()
	}

	@Test
	fun `Switching to Scene hides tags and body and shows the picker button`() {
		val component = FakeCreate(CreateTimeLineEvent.State(projectDef = projectDef))
		show(component)

		compose.onNodeWithTag(TIME_LINE_CREATE_TAGS_TAG).assertExists()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONTENT_TAG).assertExists()
		compose.onNodeWithTag(TIME_LINE_CREATE_TITLE_TAG).assertExists()
		compose.onNodeWithTag(TIME_LINE_CREATE_SUBJECT_TAG).assertDoesNotExist()

		compose.onNodeWithText("SCENE").performClick()
		compose.waitForIdle()

		assertEquals(TimelineKind.SCENE, component.state.value.kind)
		compose.onNodeWithTag(TIME_LINE_CREATE_TAGS_TAG).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONTENT_TAG).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_CREATE_TITLE_TAG).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_CREATE_SUBJECT_TAG).assertIsDisplayed()
		compose.onNodeWithText("Choose scene").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_CREATE_DATE_TAG).assertExists()
	}

	@Test
	fun `Create is disabled for a placement without a subject`() {
		val scene = CatalogItem(CatalogKind.Scene, 4, "Down the Rabbit Hole")
		val component = FakeCreate(
			CreateTimeLineEvent.State(projectDef = projectDef, kind = TimelineKind.SCENE, catalog = listOf(scene))
		)
		show(component)

		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsNotEnabled()

		component.setSubject(scene)
		compose.waitForIdle()

		compose.onNodeWithText("Down the Rabbit Hole").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsEnabled()
	}

	@Test
	fun `An event can be created without a subject`() {
		show(FakeCreate(CreateTimeLineEvent.State(projectDef = projectDef)))

		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsEnabled()
	}

	@Test
	fun `The lane field offers lanes in use`() {
		val component = FakeCreate(
			CreateTimeLineEvent.State(projectDef = projectDef, lanes = listOf("Alice", "Queen"))
		)
		show(component)

		compose.onNodeWithText("Queen").assertIsDisplayed().performClick()
		compose.waitForIdle()

		assertEquals("Queen", component.state.value.lane)
		compose.onNodeWithTag(TIME_LINE_CREATE_LANE_TAG).assertExists()
	}

	@Test
	fun `A dated timeline shows the date row and disables Create until the start is valid`() {
		val component = FakeCreate(
			CreateTimeLineEvent.State(
				projectDef = projectDef,
				mode = TimelineMode.DATED,
				calendar = CalendarDef(epochName = "Landing"),
			)
		)
		show(component)

		compose.onNodeWithTag(TIME_LINE_CREATE_DATE_TAG).assertDoesNotExist()
		compose.onNodeWithTag("$TIME_LINE_CREATE_START_TAG-year").assertExists()
		compose.onNodeWithTag(TIME_LINE_CREATE_END_TAG + "-year").assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsNotEnabled()

		compose.onNodeWithTag("$TIME_LINE_CREATE_START_TAG-day").performTextInput("400")
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsNotEnabled()

		compose.onNodeWithTag("$TIME_LINE_CREATE_START_TAG-year").performTextInput("12")
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsNotEnabled()

		compose.onNodeWithTag("$TIME_LINE_CREATE_START_TAG-day").performTextClearance()
		compose.onNodeWithTag("$TIME_LINE_CREATE_START_TAG-day").performTextInput("40")
		compose.waitForIdle()

		assertEquals(StoryDate(mapOf("year" to 12L, "day" to 40L)), component.state.value.start)
		compose.onNodeWithTag(TIME_LINE_CREATE_CONFIRM_TAG).assertIsEnabled()

		compose.onNodeWithTag(TIME_LINE_CREATE_ADD_END_TAG).performClick()
		compose.waitForIdle()
		compose.onNodeWithTag("$TIME_LINE_CREATE_END_TAG-year").assertExists()
	}
}
