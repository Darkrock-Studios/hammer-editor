import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.timeline.*
import io.mockk.*
import org.junit.Rule
import org.junit.Test

class TimeLineOverviewUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private fun componentSetup(data: TimeLineOverview.State): TimeLineOverview {
		val component: TimeLineOverview = mockk()

		val observer = slot<(TimeLineOverview.State) -> Unit>()
		val stateValue: Value<TimeLineOverview.State> = mockk()
		every { stateValue.subscribe(capture(observer)) } just Runs
		every { stateValue.unsubscribe(any()) } just Runs
		every { stateValue.value } returns data
		every { component.state } returns stateValue

		return component
	}

	@Test
	fun `Event Overview No Events`() {
		val data = TimeLineOverview.State(
			timeLine = TimeLineContainer(
				events = emptyList()
			)
		)
		val component = componentSetup(data)

		compose.setContent {
			TimeLineOverviewUi(
				component = component,
				scope = scope,
				showCreate = {},
				viewEvent = {}
			)
		}

		compose.onNodeWithText("No Events").assertIsDisplayed()
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `Event Overview Events`() {
		val viewEvent = mockk<(Int) -> Unit>()
		every { viewEvent.invoke(any()) } just Runs

		val data = TimeLineOverview.State(
			timeLine = TimeLineContainer(
				events = fakeEvents()
			)
		)
		val component = componentSetup(data)

		compose.setContent {
			TimeLineOverviewUi(
				component = component,
				scope = scope,
				showCreate = {},
				viewEvent = viewEvent
			)
		}

		compose.onNodeWithText("No Events").assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).assertIsDisplayed()
		compose.onAllNodesWithTag(EVENT_CARD_TAG).apply {
			get(0).performClick()
			verify(exactly = 1) { viewEvent.invoke(0) }
		}
		/*
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).performMouseInput {
			dragAndDrop()
		}
		*/
	}

	/*
	@Test
	fun `Event Overview Show Create`() {
		val data = TimeLineOverview.State(
			timeLine = TimeLineContainer(
				events = emptyList()
			)
		)
		val component = componentSetup(data)

		val showCreate = mockk<() -> Unit>()
		every { showCreate.invoke() } just Runs

		compose.setContent {
			TimeLineOverviewUi(
				component = component,
				scope = scope,
				showCreate = showCreate,
				viewEvent = {}
			)
		}

		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).performClick()
		verify(exactly = 1) { showCreate.invoke() }
	}
	*/

	@Test
	fun `Event Card Click`() {
		val viewEvent = mockk<(Int) -> Unit>()
		every { viewEvent.invoke(any()) } just Runs

		val date = "test date"
		val content = "test content"
		val event = TimeLineEvent(
			id = 0,
			order = 0,
			date = date,
			content = content
		)

		compose.setContent {
			EventCard(
				event = event,
				isDragging = false,
				viewEvent = viewEvent
			)
		}

		compose.onNodeWithText(date).assertIsDisplayed()
		compose.onNodeWithText(event.content).assertIsDisplayed()

		compose.onNodeWithTag(EVENT_CARD_TAG).performClick()
		verify(exactly = 1) { viewEvent.invoke(event.id) }
	}

	@Test
	fun `Event Card Content and Date`() {
		val date = "test date"
		val content = "test content"
		val event = TimeLineEvent(
			id = 0,
			order = 0,
			date = date,
			content = content
		)

		compose.setContent {
			EventCard(
				event = event,
				isDragging = false,
				viewEvent = {}
			)
		}

		compose.onNodeWithText(date).assertIsDisplayed()
		compose.onNodeWithText(event.content).assertIsDisplayed()
	}

	@Test
	fun `Event Card Content No Date`() {
		val content = "test content"
		val event = TimeLineEvent(
			id = 0,
			order = 0,
			date = null,
			content = content
		)

		compose.setContent {
			EventCard(
				event = event,
				isDragging = false,
				viewEvent = {}
			)
		}

		compose.onNodeWithTag(EVENT_CARD_DATE_TAG).assertDoesNotExist()
		compose.onNodeWithText(event.content).assertIsDisplayed()
	}

	@Test
	fun `Event Card Content Truncate`() {
		val content = "x".repeat(EVENT_CARD_MAX_CONTENT_LENGTH + 1)
		val event = TimeLineEvent(
			id = 0,
			order = 0,
			date = null,
			content = content
		)

		compose.setContent {
			EventCard(
				event = event,
				isDragging = false,
				viewEvent = {}
			)
		}

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertTextSatisfies { text ->
				text.length <= EVENT_CARD_MAX_CONTENT_LENGTH
			}
	}
}