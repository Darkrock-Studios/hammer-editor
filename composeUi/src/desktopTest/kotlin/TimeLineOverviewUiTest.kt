import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.compose.LocalImageNames
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.timeline.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TimeLineOverviewUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private class FakeOverview(
		rows: List<TimelineRow>,
		lanes: List<LaneCount> = emptyList(),
		mode: TimelineMode = TimelineMode.MANUAL,
		undatedIds: List<Int> = emptyList(),
	) : TimeLineOverview {
		private val _state = MutableValue(
			TimeLineOverview.State(rows = rows, lanes = lanes, mode = mode, undatedIds = undatedIds)
		)
		override val state: Value<TimeLineOverview.State> = _state
		override val rankedTags: Value<List<TagCount>> = MutableValue(emptyList())

		override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = true

		override fun setLaneFilter(lanes: Set<String>) {
			_state.value = _state.value.copy(laneFilter = lanes)
		}

		override fun toggleLane(lane: String) {
			val filter = _state.value.laneFilter
			setLaneFilter(if (lane in filter) filter - lane else filter + lane)
		}
	}

	private fun row(event: TimeLineEvent, title: String = event.title.orEmpty(), laneIndex: Int? = null) = TimelineRow(
		event = event,
		title = title,
		subtitle = null,
		whenText = event.label.orEmpty(),
		laneIndex = laneIndex,
	)

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun showOverview(component: TimeLineOverview, viewEvent: (Int) -> Unit = {}) {
		compose.setContent {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					TimeLineOverviewUi(
						component = component,
						scope = scope,
						showCreate = {},
						viewEvent = viewEvent,
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
	}

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun showCard(row: TimelineRow, onClick: () -> Unit = {}, wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
		compose.setContent {
			wrap {
				SharedTransitionLayout {
					AnimatedVisibility(visible = true) {
						EventCard(
							row = row,
							isLast = true,
							activeTags = emptySet(),
							onTagClick = {},
							onClick = onClick,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@AnimatedVisibility,
						)
					}
				}
			}
		}
	}

	@Test
	fun `Event Overview No Events`() {
		showOverview(FakeOverview(emptyList()))

		compose.onNodeWithText("No Events").assertIsDisplayed()
	}

	@Test
	fun `Event Overview Events`() {
		val viewed = mutableListOf<Int>()
		showOverview(FakeOverview(fakeEvents().map { row(it) }), viewEvent = { viewed += it })

		compose.onNodeWithText("No Events").assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).assertIsDisplayed()
		compose.onAllNodesWithTag(EVENT_CARD_TAG)[0].performClick()
		assertEquals(listOf(0), viewed)
	}

	@Test
	fun `Search filters the list on the prose a reader sees`() {
		showOverview(
			FakeOverview(
				listOf(
					row(TimeLineEvent(id = 0, order = 0, label = null, content = "A well\\-known road")),
					row(TimeLineEvent(id = 1, order = 1, label = null, content = "An unrelated event")),
				)
			)
		)

		compose.onNodeWithContentDescription("Search timeline").performClick()
		compose.onNodeWithTag(TIME_LINE_SEARCH_TAG).performTextInput("well-known")

		// The query never contains the stored escape, so only resolving the content can match it.
		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)
		compose.onNodeWithText("An unrelated event").assertDoesNotExist()
	}

	@Test
	fun `Search matches a placement on its subject's name`() {
		showOverview(
			FakeOverview(
				listOf(
					row(TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 4), title = "The Pool of Tears"),
					row(TimeLineEvent(id = 1, order = 1, content = "An unrelated event")),
				)
			)
		)

		compose.onNodeWithContentDescription("Search timeline").performClick()
		compose.onNodeWithTag(TIME_LINE_SEARCH_TAG).performTextInput("pool")

		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)
		compose.onNodeWithText("The Pool of Tears").assertIsDisplayed()
	}

	@Test
	fun `A lane chip filters the cards to that lane`() {
		val component = FakeOverview(
			rows = listOf(
				row(TimeLineEvent(id = 0, order = 0, title = "Fall", lane = "Alice"), laneIndex = 0),
				row(TimeLineEvent(id = 1, order = 1, title = "Croquet", lane = "Queen"), laneIndex = 1),
				row(TimeLineEvent(id = 2, order = 2, title = "Unlaned")),
			),
			lanes = listOf(LaneCount("Alice", 1), LaneCount("Queen", 1)),
		)
		showOverview(component)
		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(3)

		compose.onNodeWithTag(TIME_LINE_LANE_FILTER_TAG).assertIsDisplayed()
		compose.onNodeWithText("Queen · 1").performClick()

		assertEquals(setOf("Queen"), component.state.value.laneFilter)
		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)
		compose.onNodeWithText("Croquet").assertIsDisplayed()
		compose.onNodeWithText("Fall").assertDoesNotExist()
	}

	@Test
	fun `A card shows its kind glyph, lane swatch and milestone mark`() {
		val event = TimeLineEvent(
			id = 0,
			order = 0,
			kind = TimelineKind.SCENE,
			subject = 4,
			label = "Noon",
			lane = "Alice",
			milestone = true,
		)
		showCard(TimelineRow(event, title = "Down the hole", subtitle = "Chapter I", whenText = "Noon", laneIndex = 0))

		compose.onNodeWithTag(EVENT_CARD_KIND_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertContentDescriptionEquals("Scene")
		compose.onNodeWithText("§", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_LANE_TAG, useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_MILESTONE_TAG, useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("Down the hole", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("CHAPTER I", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `A plain card has no lane swatch or milestone mark`() {
		showCard(row(TimeLineEvent(id = 0, order = 0, content = "Rain")))

		compose.onNodeWithTag(EVENT_CARD_KIND_TAG, useUnmergedTree = true).assertContentDescriptionEquals("Event")
		compose.onNodeWithTag(EVENT_CARD_LANE_TAG, useUnmergedTree = true).assertDoesNotExist()
		compose.onNodeWithTag(EVENT_CARD_MILESTONE_TAG, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `A placement whose subject is gone says so`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.ENTRY, subject = 9, cachedTitle = "Lost")
		showCard(TimelineRow(event, title = "Lost", subtitle = null, whenText = "", laneIndex = null, subjectMissing = true))

		compose.onNodeWithText("SUBJECT MISSING", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("Lost", useUnmergedTree = true).assertIsDisplayed()
	}

	@Test
	fun `Event Card Click`() {
		var clicked = 0
		val event = TimeLineEvent(id = 0, order = 0, label = "test date", content = "test content")
		showCard(row(event), onClick = { clicked++ })

		compose.onNodeWithText("TEST DATE").assertIsDisplayed()
		compose.onNodeWithText(event.content).assertIsDisplayed()

		compose.onNodeWithTag(EVENT_CARD_TAG).performClick()
		assertEquals(1, clicked)
	}

	@Test
	fun `Event Card Content and Date`() {
		val event = TimeLineEvent(id = 0, order = 0, label = "test date", content = "test content")
		showCard(row(event))

		compose.onNodeWithText("TEST DATE").assertIsDisplayed()
		compose.onNodeWithText(event.content).assertIsDisplayed()
	}

	@Test
	fun `Event Card Content No Date`() {
		val event = TimeLineEvent(id = 0, order = 0, label = null, content = "test content")
		showCard(row(event))

		compose.onNodeWithTag(EVENT_CARD_DATE_TAG, useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("UNDATED").assertIsDisplayed()
		compose.onNodeWithText(event.content).assertIsDisplayed()
	}

	@Test
	fun `Event Card Content Truncate`() {
		val event = TimeLineEvent(id = 0, order = 0, label = null, content = "x".repeat(EVENT_CARD_MAX_CONTENT_LENGTH + 1))
		showCard(row(event))

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertTextSatisfies { text ->
				text.length <= EVENT_CARD_MAX_CONTENT_LENGTH
			}
	}

	@Test
	fun `Event Card never shows an image's alt text`() {
		val content = "x ".repeat(100) + "\n\n![Cheshire Cat](hammer://image/3)\n\n" + "tail ".repeat(20)
		showCard(row(TimeLineEvent(id = 0, order = 0, label = null, content = content)))

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertTextSatisfies { text ->
				text.length <= EVENT_CARD_MAX_CONTENT_LENGTH &&
					Regex("""\n\n\*[^*\n]+\*\n\n""").containsMatchIn(text) &&
					!text.contains("Cheshire Cat") &&
					!text.contains("hammer://")
			}
	}

	@Test
	fun `Event Card shows an image by its current name`() {
		val event = TimeLineEvent(id = 0, order = 0, label = null, content = "Met\n\n![image](hammer://image/3)")
		showCard(row(event), wrap = { content ->
			CompositionLocalProvider(LocalImageNames provides { id -> if (id == 3) "Cheshire Cat" else null }) {
				content()
			}
		})

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertTextSatisfies { text -> text.text == "Met\n\n*Cheshire Cat*" }
	}

	@Test
	fun `A dated timeline with undated entries offers to open the first`() {
		val opened = mutableListOf<Int>()
		val rows = listOf(
			row(TimeLineEvent(id = 1, order = 0, title = "Dated")),
			row(TimeLineEvent(id = 2, order = 1, title = "Legacy")),
		)
		showOverview(FakeOverview(rows, mode = TimelineMode.DATED, undatedIds = listOf(2)), viewEvent = { opened += it })

		compose.onNodeWithText("1 entry has no date").assertIsDisplayed()
		compose.onNodeWithText("Open first").performClick()

		assertEquals(listOf(2), opened)
	}

	@Test
	fun `A manual timeline shows no undated notice`() {
		showOverview(FakeOverview(listOf(row(TimeLineEvent(id = 1, order = 0, title = "One")))))

		compose.onNodeWithTag(TIME_LINE_UNDATED_TAG).assertDoesNotExist()
	}
}
