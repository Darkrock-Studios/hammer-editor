import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack as AnimatedChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.common.components.timeline.basicPreview
import com.darkrockstudios.apps.hammer.common.compose.LocalImageNames
import com.darkrockstudios.apps.hammer.common.compose.LocalRootFabState
import com.darkrockstudios.apps.hammer.common.compose.RootFabState
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.ScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.WindowHeightSizeClass
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.timeline.*
import com.darkrockstudios.apps.hammer.common.timeline.drawing.LocalTimelineLayoutHost
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineLayoutHost
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_DRAWING_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TIMELINE_HORIZONTAL_SCROLL_TAG
import com.darkrockstudios.apps.hammer.common.timeline.drawing.testTag
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TimeLineOverviewUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private class FakeOverview(
		rows: List<TimelineRow>,
		lanes: List<LaneCount> = emptyList(),
		mode: TimelineMode = TimelineMode.MANUAL,
		undatedIds: List<Int> = emptyList(),
		storyFrameAvailable: Boolean = false,
		tags: List<TagCount> = emptyList(),
		private val references: Map<Int, List<CatalogItem>> = emptyMap(),
		private val excerpts: Map<Int, String> = emptyMap(),
		timelines: List<TimelineSummary> = emptyList(),
		selection: TimelineSelection = TimelineSelection.Main,
		orphanCount: Int = 0,
	) : TimeLineOverview {
		private val _state = MutableValue(
			TimeLineOverview.State(
				rows = rows,
				lanes = lanes,
				mode = mode,
				undatedIds = undatedIds,
				storyFrameAvailable = storyFrameAvailable,
				timelines = timelines,
				selection = selection,
				orphanCount = orphanCount,
			)
		)
		override val state: Value<TimeLineOverview.State> = _state
		override val rankedTags: Value<List<TagCount>> = MutableValue(tags)

		override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = true
		val orphansMovedTo = mutableListOf<String>()
		var orphansDeleted = 0
		/** How many of the bucket's events a move leaves behind. */
		var orphansStaying = 0

		override suspend fun moveOrphansTo(target: String): Int {
			orphansMovedTo += target
			return _state.value.orphanCount - orphansStaying
		}

		override suspend fun deleteOrphans(): Int {
			orphansDeleted++
			return _state.value.orphanCount
		}

		fun setLaneFilter(lanes: Set<String>) {
			_state.value = _state.value.copy(laneFilter = lanes)
		}

		override fun setSearchQuery(query: String) {
			_state.value = _state.value.copy(searchQuery = query)
		}

		override fun toggleTag(tag: String) {
			val filter = _state.value.tagFilter
			_state.value = _state.value.copy(tagFilter = if (tag in filter) filter - tag else filter + tag)
		}

		override fun toggleLane(lane: String) {
			val filter = _state.value.laneFilter
			setLaneFilter(if (lane in filter) filter - lane else filter + lane)
		}

		override fun clearFilters() {
			_state.value = _state.value.copy(tagFilter = emptySet(), laneFilter = emptySet())
		}

		override fun setView(view: TimelineView) {
			_state.value = _state.value.copy(view = view)
		}

		override fun canFrame(eventId: Int): Boolean = false

		private fun previewOf(eventId: Int): EventPreview? =
			_state.value.rows?.firstOrNull { it.event.id == eventId }?.basicPreview()?.copy(
				excerpt = excerpts[eventId],
				references = references[eventId].orEmpty(),
			)

		private val _previews = MutableValue<Map<Int, EventPreview>>(emptyMap())
		override val previews: Value<Map<Int, EventPreview>> = _previews

		override fun requestPreview(eventId: Int) {
			previewOf(eventId)?.let { _previews.value += eventId to it }
		}

		override fun setFrame(frame: TimelineFrame) {
			_state.value = _state.value.copy(frame = frame)
		}


		/** Rows arriving after the first frame, as they do from the timeline service. */
		fun load(rows: List<TimelineRow>) {
			_state.value = _state.value.copy(rows = rows)
		}
	}

	private fun row(event: TimeLineEvent, title: String = event.title.orEmpty(), laneIndex: Int? = null) = TimelineRow(
		event = event,
		title = title,
		subtitle = null,
		whenText = event.label.orEmpty(),
		laneIndex = laneIndex,
	)

	private val desktopScreen = ScreenCharacteristics(
		isWide = true,
		windowWidthClass = WindowWidthSizeClass.Expanded,
		windowHeightClass = WindowHeightSizeClass.Expanded,
		needsExplicitClose = false,
	)

	private val phoneScreen = ScreenCharacteristics(
		isWide = false,
		windowWidthClass = WindowWidthSizeClass.Compact,
		windowHeightClass = WindowHeightSizeClass.Medium,
		needsExplicitClose = false,
	)

	/** Without a [screen] the overview sees the default screen characteristics. */
	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun showOverview(
		component: TimeLineOverview,
		viewEvent: (Int) -> Unit = {},
		screen: ScreenCharacteristics? = null,
		size: DpSize? = null,
		rootFab: RootFabState = RootFabState(),
		shown: () -> Boolean = { true },
		layoutHost: TimelineLayoutHost = TimelineLayoutHost(EmptyCoroutineContext),
		lastLayout: LastTimelineLayout? = null,
		switcher: TimelineSwitcherActions = TimelineSwitcherActions.None,
		uiScope: CoroutineScope = scope,
		onOrphansLeft: (Int) -> Unit = {},
	) {
		compose.setContent {
			// Keeps saved state while the overview is hidden, as the timeline's stack does.
			val saved = rememberSaveableStateHolder()
			val content: @Composable () -> Unit = {
				Box(modifier = if (size != null) Modifier.requiredSize(size) else Modifier) {
					SharedTransitionLayout {
						AnimatedVisibility(visible = true) {
							TimeLineOverviewUi(
								component = component,
								scope = uiScope,
								showCreate = {},
								viewEvent = viewEvent,
								switcher = switcher,
								sharedTransitionScope = this@SharedTransitionLayout,
								animatedVisibilityScope = this@AnimatedVisibility,
								lastLayout = lastLayout ?: remember { LastTimelineLayout() },
								onOrphansLeft = onOrphansLeft,
							)
						}
					}
				}
			}
			CompositionLocalProvider(LocalRootFabState provides rootFab, LocalTimelineLayoutHost provides layoutHost) {
				if (shown()) {
					saved.SaveableStateProvider("overview") {
						if (screen != null) {
							CompositionLocalProvider(LocalScreenCharacteristic provides screen, content = content)
						} else {
							content()
						}
					}
				}
			}
		}
	}

	private fun showPhoneOverview(component: TimeLineOverview, viewEvent: (Int) -> Unit = {}) =
		showOverview(component, viewEvent, phoneScreen, DpSize(360.dp, 760.dp))

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun showCard(
		row: TimelineRow,
		onClick: () -> Unit = {},
		whenColumn: Boolean = true,
		dated: Boolean = false,
		placementExcerpt: String? = null,
		laneCount: Int = 2,
		wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
	) {
		compose.setContent {
			wrap {
				SharedTransitionLayout {
					AnimatedVisibility(visible = true) {
						TimelineRailRow(
							row = row,
							laneCount = laneCount,
							whenColumn = whenColumn,
							onClick = onClick,
							dated = dated,
							placementExcerpt = placementExcerpt,
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
	fun `On a phone a kept search shows its field from the first frame`() {
		val component = FakeOverview(
			listOf(
				row(TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 4), title = "The Pool of Tears"),
				row(TimeLineEvent(id = 1, order = 1, content = "An unrelated event")),
			)
		)
		component.setSearchQuery("pool")

		showPhoneOverview(component)

		compose.onNodeWithTag(TIME_LINE_SEARCH_TAG).assertIsDisplayed().assertTextContains("pool")
		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)
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
	fun `The filters and search survive the overview leaving composition`() {
		val component = FakeOverview(
			rows = listOf(
				row(TimeLineEvent(id = 0, order = 0, title = "Fall", lane = "Alice", tags = setOf("act-one")), laneIndex = 0),
				row(TimeLineEvent(id = 1, order = 1, title = "Croquet", lane = "Queen", tags = setOf("act-one")), laneIndex = 1),
				row(TimeLineEvent(id = 2, order = 2, title = "Tea", lane = "Alice", tags = setOf("act-one")), laneIndex = 0),
				row(TimeLineEvent(id = 3, order = 3, title = "Trial", lane = "Alice")),
			),
			lanes = listOf(LaneCount("Alice", 3), LaneCount("Queen", 1)),
			tags = listOf(TagCount("act-one", 3)),
		)
		var shown by mutableStateOf(true)
		showOverview(component, screen = desktopScreen, shown = { shown })
		compose.onNodeWithText("act-one · 3").performClick()
		compose.onNodeWithText("Alice · 3").performClick()
		compose.onNode(hasSetTextAction()).performTextInput("tea")
		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)

		// As when an event opened from the list covers the overview, and is closed again.
		compose.runOnIdle { shown = false }
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).assertDoesNotExist()
		compose.runOnIdle { shown = true }

		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(1)
		compose.onNodeWithText("Tea").assertIsDisplayed()
	}

	@Test
	fun `On a phone every lane chip scrolls into reach`() {
		val lanes = listOf("Alice", "The Queen of Hearts", "The Mock Turtle", "The Cheshire Cat")
		val component = FakeOverview(
			rows = lanes.mapIndexed { i, lane ->
				row(TimeLineEvent(id = i, order = i, title = "Event $i", lane = lane), laneIndex = i)
			},
			lanes = lanes.map { LaneCount(it, 1) },
		)
		showPhoneOverview(component)

		lanes.forEach { lane -> compose.onNodeWithText("$lane · 1").assertExists() }
		compose.onNodeWithText("The Cheshire Cat · 1").performScrollTo().assertIsDisplayed().performClick()

		assertEquals(setOf("The Cheshire Cat"), component.state.value.laneFilter)
	}

	@Test
	fun `On a desktop many lanes keep every filter on one line`() {
		val long = "The crew of the Rocinante and everyone they picked up along the way"
		val lanes = listOf("Julie & the Scopuli", "Holden & crew", "Miller", "Protomolecule", "The Ring builders", long)
		val component = FakeOverview(
			rows = lanes.mapIndexed { i, lane ->
				row(TimeLineEvent(id = i, order = i, title = "Event $i", lane = lane), laneIndex = i)
			},
			lanes = lanes.map { LaneCount(it, 1) },
			tags = listOf(TagCount("act-one", 2)),
		)
		showOverview(component, screen = desktopScreen, size = DpSize(1000.dp, 700.dp))

		(lanes.map { "$it · 1" } + "ALL · 6").forEach { label ->
			val layout = textLayout(label)
			assertEquals(1, layout.lineCount, "$label wrapped")
			val fits = layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth
			assertEquals(label != "$long · 1", fits, "$label is cut short: ${fits.not()}")
		}
		compose.onNodeWithText("The Ring builders · 1").performScrollTo().performClick()
		assertEquals(setOf("The Ring builders"), component.state.value.laneFilter)
	}

	private fun textLayout(text: String): TextLayoutResult = textLayout(compose.onNodeWithText(text, useUnmergedTree = true))

	private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
		val results = mutableListOf<TextLayoutResult>()
		node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
		return results.single()
	}

	@Test
	fun `A wide list has a label column only when some entry has a label`() {
		val unlabelled = FakeOverview(listOf(row(TimeLineEvent(id = 0, order = 0, title = "Fall"))))
		showOverview(unlabelled, screen = desktopScreen, size = DpSize(1000.dp, 700.dp))
		// With no column, the kind line carries the (empty) label, as on a phone.
		compose.onNodeWithTag(EVENT_CARD_DATE_TAG, useUnmergedTree = true).assertTextEquals("EVENT")

		val labelled = FakeOverview(
			listOf(
				row(TimeLineEvent(id = 0, order = 0, title = "Fall")),
				row(TimeLineEvent(id = 1, order = 1, title = "Tea", label = "Six o'clock")),
			),
		)
		showOverview(labelled, screen = desktopScreen, size = DpSize(1000.dp, 700.dp))
		compose.onNodeWithText("SIX O'CLOCK", useUnmergedTree = true).assertIsDisplayed()
		compose.onAllNodesWithText("EVENT", useUnmergedTree = true).assertCountEquals(2)
	}

	@Test
	fun `A placed scene sits on its lane's rail with its chapter and excerpt`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 4, label = "Noon", lane = "Alice")
		showCard(
			TimelineRow(event, title = "Down the hole", subtitle = "Chapter I", whenText = "Noon", laneIndex = 0),
			placementExcerpt = "Alice was beginning to get very tired",
		)

		compose.onNodeWithTag(EVENT_CARD_KIND_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertContentDescriptionEquals("Scene")
		compose.onNodeWithText("§", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_LANE_TAG, useUnmergedTree = true).assertContentDescriptionEquals("Lane Alice")
		compose.onNodeWithTag(EVENT_CARD_MILESTONE_TAG, useUnmergedTree = true).assertDoesNotExist()
		compose.onNodeWithText("NOON", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("§ SCENE · CHAPTER I", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("Down the hole", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertTextEquals("Alice was beginning to get very tired")
	}

	@Test
	fun `On a phone many lanes leave the title most of the row`() {
		val title = "The storm that took the roof off the mill and half the village with it"
		val event = TimeLineEvent(id = 0, order = 0, title = title, lane = "Lane 20")
		showCard(
			TimelineRow(event, title = title, subtitle = null, whenText = "", laneIndex = 19),
			whenColumn = false,
			laneCount = 20,
			wrap = { content -> Box(Modifier.requiredWidth(360.dp)) { content() } },
		)

		val titleNode = compose.onNodeWithTag(EVENT_CARD_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNode()
		val titleWidth = with(compose.density) { titleNode.boundsInRoot.width.toDp() }
		assertTrue(titleWidth >= 180.dp, "title is only $titleWidth wide")
		compose.onNodeWithTag(EVENT_CARD_LANE_TAG, useUnmergedTree = true).assertContentDescriptionEquals("Lane Lane 20")
	}

	@Test
	fun `A placement holds its excerpt line open while the excerpt is read`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 4)
		showCard(TimelineRow(event, title = "Down the hole", subtitle = null, whenText = "", laneIndex = null))

		compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true).assertTextEquals("")
	}

	@Test
	fun `A placement with nothing to excerpt has no excerpt line`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 4)
		showCard(TimelineRow(event, title = "Down the hole", subtitle = null, whenText = "", laneIndex = null), placementExcerpt = "")

		compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `A milestone rules across the row`() {
		val event = TimeLineEvent(id = 0, order = 0, title = "The storm", content = "Wind takes the roof.", milestone = true)
		showCard(row(event))

		compose.onNodeWithTag(EVENT_CARD_MILESTONE_TAG, useUnmergedTree = true)
			.assertIsDisplayed()
			.assertContentDescriptionEquals("Milestone")
		compose.onNodeWithTag(EVENT_CARD_KIND_TAG, useUnmergedTree = true).assertContentDescriptionEquals("Event")
	}

	@Test
	fun `A plain event on the spine has no lane`() {
		showCard(row(TimeLineEvent(id = 0, order = 0, content = "Rain")))

		compose.onNodeWithTag(EVENT_CARD_KIND_TAG, useUnmergedTree = true).assertContentDescriptionEquals("Event")
		compose.onNodeWithTag(EVENT_CARD_LANE_TAG, useUnmergedTree = true).assertDoesNotExist()
		compose.onNodeWithTag(EVENT_CARD_MILESTONE_TAG, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `On a phone the label and lane join the kind line`() {
		val event = TimeLineEvent(id = 0, order = 0, title = "Fall", label = "Noon", lane = "Alice", content = "Down she went.")
		showCard(row(event, laneIndex = 0), whenColumn = false)

		compose.onNodeWithTag(EVENT_CARD_DATE_TAG, useUnmergedTree = true).assertTextEquals("NOON · EVENT · ALICE")
	}

	@Test
	fun `A placement whose subject is gone says so`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.ENTRY, subject = 9, cachedTitle = "Lost")
		showCard(TimelineRow(event, title = "Lost", subtitle = null, whenText = "", laneIndex = null, subjectMissing = true))

		compose.onNodeWithText(" · SUBJECT MISSING", useUnmergedTree = true).assertIsDisplayed()
		compose.onNodeWithText("Lost", useUnmergedTree = true).assertIsDisplayed()
	}

	@Test
	fun `A placed scene that is archived says so`() {
		val event = TimeLineEvent(id = 0, order = 0, kind = TimelineKind.SCENE, subject = 9)
		showCard(TimelineRow(event, title = "Landfall", subtitle = null, whenText = "", laneIndex = null, subjectArchived = true))

		compose.onNodeWithTag(EVENT_CARD_ARCHIVED_TAG, useUnmergedTree = true).assertTextEquals(" · ARCHIVED")
		compose.onNodeWithText("Landfall", useUnmergedTree = true).assertIsDisplayed()
	}

	@Test
	fun `Event Card Click`() {
		var clicked = 0
		val event = TimeLineEvent(id = 0, order = 0, label = "test date", content = "test content")
		showCard(row(event), onClick = { clicked++ })

		compose.onNodeWithTag(EVENT_CARD_TAG).performClick()
		assertEquals(1, clicked)
	}

	@Test
	fun `An untitled event is titled by its first line and excerpted from the rest`() {
		val event = TimeLineEvent(id = 0, order = 0, label = "test date", content = "The storm\n\nWind takes the roof.")
		showCard(row(event, title = "The storm"))

		compose.onNodeWithText("TEST DATE").assertIsDisplayed()
		compose.onNodeWithTag(EVENT_CARD_TITLE_TAG, useUnmergedTree = true).assertTextEquals("The storm")
		compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true).assertTextEquals("Wind takes the roof.")
	}

	@Test
	fun `A dated card formats its start and end in the calendar's words`() {
		val event = TimeLineEvent(id = 0, order = 0, label = "Spring", content = "Arrival")
		val row = row(event).copy(
			start = StoryDate(linkedMapOf("year" to 1802L, "month" to 5L, "day" to 12L)),
			end = StoryDate(linkedMapOf("year" to 1802L, "month" to 5L, "day" to 14L)),
			calendar = CalendarDef(CalendarPreset.GREGORIAN),
		)
		showCard(row, dated = true)

		compose.onNodeWithText("12 MAY 1802 → 14 MAY 1802").assertIsDisplayed()
		compose.onNodeWithText("SPRING").assertDoesNotExist()
	}

	@Test
	fun `A dated story card names the epoch`() {
		val event = TimeLineEvent(id = 0, order = 0, content = "Before")
		val row = row(event).copy(
			start = StoryDate(linkedMapOf("year" to -3L, "day" to 2L)),
			calendar = CalendarDef(epochName = "Landing"),
		)
		showCard(row, dated = true)

		compose.onNodeWithText("YEAR 3 BEFORE LANDING · DAY 2").assertIsDisplayed()
	}

	@Test
	fun `An entry with no start on a dated timeline is undated`() {
		showCard(row(TimeLineEvent(id = 0, order = 0, label = null, content = "test content")), dated = true)

		compose.onNodeWithTag(EVENT_CARD_DATE_TAG, useUnmergedTree = true).assertTextEquals("UNDATED")
	}

	@Test
	fun `A manual entry with no label leaves the label empty`() {
		showCard(row(TimeLineEvent(id = 0, order = 0, label = null, content = "test content")))

		compose.onNodeWithTag(EVENT_CARD_DATE_TAG, useUnmergedTree = true).assertTextEquals("")
	}

	@Test
	fun `The excerpt keeps to one line`() {
		val content = "Heading\n\n" + "word ".repeat(200)
		showCard(row(TimeLineEvent(id = 0, order = 0, title = "Heading", content = content)))

		assertEquals(1, textLayout(compose.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)).lineCount)
	}

	@Test
	fun `The excerpt never shows an image's alt text`() {
		val content = "Met\n\n![Cheshire Cat](hammer://image/3)\n\ntail"
		showCard(row(TimeLineEvent(id = 0, order = 0, title = "Met", content = content)))

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertTextSatisfies { text -> !text.contains("Cheshire Cat") && !text.contains("hammer://") }
	}

	@Test
	fun `The excerpt names an image by its current name`() {
		val event = TimeLineEvent(id = 0, order = 0, content = "Met\n\n![image](hammer://image/3)")
		showCard(row(event, title = "Met"), wrap = { content ->
			CompositionLocalProvider(LocalImageNames provides { id -> if (id == 3) "Cheshire Cat" else null }) {
				content()
			}
		})

		compose
			.onNodeWithTag(EVENT_CARD_CONTENT_TAG, useUnmergedTree = true)
			.assertTextSatisfies { text -> text.text == "Cheshire Cat" }
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

	@Test
	fun `A dated timeline draws its rows and offers the view picker`() {
		val calendar = CalendarDef()
		val rows = listOf(
			row(TimeLineEvent(id = 1, order = 0, title = "Fall", lane = "Alice")).copy(
				start = StoryDate(mapOf("year" to 1L)),
				calendar = calendar,
				laneIndex = 0,
			),
			row(TimeLineEvent(id = 2, order = 1, title = "Croquet")).copy(
				start = StoryDate(mapOf("year" to 2L)),
				calendar = calendar,
			),
		)
		val component = FakeOverview(rows, lanes = listOf(LaneCount("Alice", 1)), mode = TimelineMode.DATED)
		showOverview(component)

		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG).assertIsDisplayed()

		chooseInDrawingMenu("To scale")
		assertEquals(TimelineView.TO_SCALE, component.state.value.view)
	}

	private fun datedRow(event: TimeLineEvent, year: Long, endYear: Long? = null, laneIndex: Int? = null) =
		row(event, laneIndex = laneIndex).copy(
			start = StoryDate(mapOf("year" to year)),
			end = endYear?.let { StoryDate(mapOf("year" to it)) },
			calendar = CalendarDef(),
		)

	private fun inCard(text: String) = hasText(text, substring = true) and hasAnyAncestor(hasTestTag(EVENT_PREVIEW_CARD_TAG))

	private fun showPreviewCard(preview: EventPreview) {
		compose.setContent {
			Box(modifier = Modifier.requiredWidth(520.dp)) {
				EventPreviewCard(preview = preview)
			}
		}
	}

	private val croquetPreview = EventPreview(eventId = 7, kind = TimelineKind.EVENT, title = "Croquet", whenText = "")

	private fun chooseInDrawingMenu(label: String) {
		compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG).performClick()
		compose.onNodeWithTag(menuChoiceTag(label)).performClick()
		compose.waitForIdle()
	}

	/** The Siege spans years one to four in the Keep; the Moat's lane is empty beside it. */
	private fun siegeBesideTheMoat() = FakeOverview(
		listOf(
			datedRow(TimeLineEvent(id = 3, order = 0, title = "The Siege", lane = "Keep"), year = 1, endYear = 4, laneIndex = 0),
			datedRow(TimeLineEvent(id = 5, order = 1, title = "Flood", lane = "Moat"), year = 9, laneIndex = 1),
		),
		lanes = listOf(LaneCount("Keep", 1), LaneCount("Moat", 1)),
		mode = TimelineMode.DATED,
	)

	/** The drawing's colour in the Moat's lane level with the middle of the Siege, which the Siege's projection tints. */
	private fun besideTheSiege(): Color {
		val drawing = compose.onNodeWithTag(TIMELINE_DRAWING_TAG)
		val origin = drawing.getBoundsInRoot()
		val siege = compose.onNodeWithTag(HitTarget.Band(3).testTag()).getBoundsInRoot()
		val moat = compose.onNodeWithTag(HitTarget.LaneHeader("Moat").testTag()).getBoundsInRoot()
		val density = compose.density.density
		val x = (((moat.left + moat.right) / 2 - origin.left).value * density).toInt()
		val y = (((siege.top + siege.bottom) / 2 - origin.top).value * density).toInt()
		// The drawing, not the root: the Siege's preview card opens a second root.
		return drawing.captureToImage().toPixelMap()[x, y]
	}

	@Test
	fun `Clicking a drawn event opens it without projecting it`() {
		val opened = mutableListOf<Int>()
		showOverview(siegeBesideTheMoat(), viewEvent = { opened += it }, screen = desktopScreen)
		val before = besideTheSiege()

		compose.onNodeWithTag(HitTarget.Band(3).testTag()).performClick()
		compose.waitForIdle()

		assertEquals(listOf(3), opened)
		assertEquals(before, besideTheSiege())
	}

	@Test
	fun `A long press projects its event across the lanes until it is released`() {
		val opened = mutableListOf<Int>()
		showOverview(siegeBesideTheMoat(), viewEvent = { opened += it }, screen = desktopScreen)
		val before = besideTheSiege()
		val siege = compose.onNodeWithTag(HitTarget.Band(3).testTag())

		siege.performTouchInput { down(center) }
		compose.mainClock.advanceTimeBy(1_000)
		compose.waitForIdle()

		assertNotEquals(before, besideTheSiege(), "the held event is projected")

		siege.performTouchInput { up() }
		compose.waitForIdle()

		assertEquals(before, besideTheSiege(), "the projection ends on release")
		assertEquals(emptyList(), opened, "A long press does not open the event")
	}

	@Test
	fun `An event that leaves the drawing during a long press is not projected when it returns`() {
		val component = siegeBesideTheMoat()
		val rows = component.state.value.rows!!
		showOverview(component, screen = desktopScreen)
		val before = besideTheSiege()
		compose.onNodeWithTag(HitTarget.Band(3).testTag()).performTouchInput { down(center) }
		compose.mainClock.advanceTimeBy(1_000)
		compose.waitForIdle()

		compose.runOnIdle { component.load(rows.filter { it.event.id != 3 }) }
		compose.waitForIdle()
		compose.runOnIdle { component.load(rows) }
		compose.waitForIdle()

		assertEquals(before, besideTheSiege(), "the press is still held, but its peek ended")
		compose.onNodeWithTag(HitTarget.Band(3).testTag()).performTouchInput { up() }
	}

	private val eightPeople = (1..8).map { CatalogItem(CatalogKind.Entry, 100 + it, "Person $it", entryType = EntryType.PERSON) }

	@Test
	fun `A preview card names three references and counts the rest`() {
		showPreviewCard(croquetPreview.copy(references = eightPeople))

		val line = hasText("Person 3", substring = true) and hasText("+5", substring = true)
		compose.onNode(line and hasAnyAncestor(hasTestTag(EVENT_PREVIEW_CARD_TAG)), useUnmergedTree = true).assertExists()
		compose.onNode(inCard("Person 4"), useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `A preview card shows the event's excerpt`() {
		showPreviewCard(croquetPreview.copy(excerpt = "The walls held for three winters."))

		compose.onNode(inCard("The walls held for three winters."), useUnmergedTree = true).assertIsDisplayed()
	}

	@Test
	fun `On a phone the drawing menu sits beside search and leaves the strip to the chips`() {
		val rows = listOf(
			datedRow(TimeLineEvent(id = 1, order = 0, title = "Fall", lane = "Alice"), year = 1, laneIndex = 0),
			datedRow(TimeLineEvent(id = 2, order = 1, title = "Croquet"), year = 2),
		)
		val component = FakeOverview(rows, lanes = listOf(LaneCount("Alice", 1)), mode = TimelineMode.DATED)
		showPhoneOverview(component)

		val search = compose.onNodeWithContentDescription("Search timeline").getUnclippedBoundsInRoot()
		val chips = compose.onNodeWithTag(TIME_LINE_LANE_FILTER_TAG).getUnclippedBoundsInRoot()
		val menu = compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
		assertTrue(menu.top < search.bottom && menu.bottom > search.top, "the menu at ${menu.top} is not in the search row")
		assertTrue(menu.bottom <= chips.top, "the menu at ${menu.top} is not above the strip")
		compose.onNodeWithText("Sequence", substring = true, ignoreCase = true).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_CALENDAR_TAG).assertDoesNotExist()

		chooseInDrawingMenu("To scale")

		assertEquals(TimelineView.TO_SCALE, component.state.value.view)
	}

	@Test
	fun `Clicking a band opens it without framing it`() {
		val opened = mutableListOf<Int>()
		val rows = listOf(
			datedRow(TimeLineEvent(id = 3, order = 0, title = "The Siege", lane = "Keep"), year = 1, endYear = 4, laneIndex = 0),
			datedRow(TimeLineEvent(id = 4, order = 1, title = "Croquet"), year = 6),
		)
		val component = FakeOverview(rows, lanes = listOf(LaneCount("Keep", 1)), mode = TimelineMode.DATED)
		showOverview(component, viewEvent = { opened += it })

		compose.onNodeWithTag(HitTarget.Band(3).testTag()).performClick()
		compose.waitForIdle()

		assertEquals(listOf(3), opened)
		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).assertDoesNotExist()
	}

	@Test
	fun `The drawing menu offers the story frame only with placed scenes`() {
		val rows = listOf(datedRow(TimeLineEvent(id = 1, order = 0, title = "Storm"), year = 1))
		showOverview(FakeOverview(rows, mode = TimelineMode.DATED))

		compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG).performClick()

		compose.onNodeWithTag(menuChoiceTag("Story")).assertIsNotEnabled()
	}

	@Test
	fun `The drawing menu switches to the story frame`() {
		val rows = listOf(
			datedRow(TimeLineEvent(id = 1, order = 0, title = "Storm"), year = 1),
			datedRow(TimeLineEvent(id = 2, order = 1, kind = TimelineKind.SCENE, subject = 4), year = 2),
		)
		val component = FakeOverview(rows, mode = TimelineMode.DATED, storyFrameAvailable = true)
		showOverview(component)

		chooseInDrawingMenu("Story")

		assertEquals(TimelineFrame.Story, component.state.value.frame)
	}

	private fun siegeAndCroquet(excerpts: Map<Int, String> = emptyMap()) = FakeOverview(
		listOf(
			datedRow(TimeLineEvent(id = 3, order = 0, title = "The Siege", lane = "Keep"), year = 1, endYear = 4, laneIndex = 0),
			// To the minute, so it is drawn as a point rather than spanning its year.
			row(TimeLineEvent(id = 4, order = 1, title = "Croquet", lane = "Keep"), laneIndex = 0).copy(
				start = StoryDate(mapOf("year" to 6L, "day" to 10L, "hour" to 6L, "minute" to 30L)),
				calendar = CalendarDef(),
			),
		),
		lanes = listOf(LaneCount("Keep", 2)),
		mode = TimelineMode.DATED,
		excerpts = excerpts,
	)

	@Test
	fun `The drawing menu names a framed band, and Everything leaves it`() {
		val component = siegeAndCroquet()
		showOverview(component, screen = desktopScreen)
		compose.runOnIdle { component.setFrame(TimelineFrame.Band(3)) }

		compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG)
			.assertIsDisplayed()
			.assertContentDescriptionContains("Framed on The Siege")

		chooseInDrawingMenu("Everything")

		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).assertDoesNotExist()
	}

	@Test
	fun `Searching the drawing keeps every event and steps through the matches`() {
		val rows = listOf(
			datedRow(TimeLineEvent(id = 1, order = 0, title = "Storm at sea"), year = 1),
			datedRow(TimeLineEvent(id = 2, order = 1, title = "Croquet"), year = 2),
			datedRow(TimeLineEvent(id = 3, order = 2, title = "Storm in a teacup"), year = 3),
		)
		showOverview(FakeOverview(rows, mode = TimelineMode.DATED), screen = desktopScreen)

		compose.onNode(hasSetTextAction()).performTextInput("storm")
		compose.waitForIdle()

		compose.onNodeWithTag(HitTarget.Ledger(2).testTag()).assertExists()
		compose.onNodeWithTag(TIME_LINE_SEARCH_MATCHES_TAG).assertTextEquals("1 OF 2")

		compose.onNode(hasSetTextAction()).performImeAction()
		compose.waitForIdle()

		compose.onNodeWithTag(TIME_LINE_SEARCH_MATCHES_TAG).assertTextEquals("2 OF 2")
	}

	@Test
	fun `Hovering a drawn row shows its preview in a tooltip`() {
		val excerpt = "Mallets are flamingos and the balls are hedgehogs."
		val component = FakeOverview(
			listOf(datedRow(TimeLineEvent(id = 7, order = 0, title = "Croquet"), year = 1)),
			mode = TimelineMode.DATED,
			excerpts = mapOf(7 to excerpt),
		)
		showOverview(component, screen = desktopScreen)
		compose.onNode(hasText(excerpt, substring = true), useUnmergedTree = true).assertDoesNotExist()

		compose.onNodeWithTag(HitTarget.Ledger(7).testTag()).performMouseInput { enter(center) }
		compose.waitForIdle()

		compose.onNode(
			hasText(excerpt, substring = true) and hasAnyAncestor(hasTestTag(EVENT_PREVIEW_CARD_TAG)),
			useUnmergedTree = true,
		).assertExists()
	}

	private fun showDatedPane(
		laneCount: Int,
		width: Dp,
		screen: ScreenCharacteristics = desktopScreen,
		laneFilter: () -> Set<String> = { emptySet() },
		layoutHost: TimelineLayoutHost = TimelineLayoutHost(EmptyCoroutineContext),
	) {
		val calendar = CalendarDef()
		val lanes = (1..laneCount).map { LaneCount("Lane $it", 1) }
		val rows = lanes.mapIndexed { i, lane ->
			row(TimeLineEvent(id = i + 1, order = i, title = "Event ${i + 1}", lane = lane.lane), laneIndex = i).copy(
				start = StoryDate(mapOf("year" to i + 1L)),
				calendar = calendar,
			)
		}
		compose.setContent {
			val state = TimeLineOverview.State(
				rows = rows,
				lanes = lanes,
				mode = TimelineMode.DATED,
				calendar = calendar,
				laneFilter = laneFilter(),
			)
			CompositionLocalProvider(LocalScreenCharacteristic provides screen, LocalTimelineLayoutHost provides layoutHost) {
				Box(modifier = Modifier.requiredSize(width, 700.dp)) {
					DatedTimelinePane(state = state, rows = rows, onTap = {})
				}
			}
		}
	}

	@Test
	fun `A dated timeline too narrow for its lanes scrolls sideways`() {
		showDatedPane(laneCount = 14, width = 600.dp)

		compose.onNodeWithTag(TIMELINE_HORIZONTAL_SCROLL_TAG).assertExists()
		val lastHeader = compose.onNodeWithTag(HitTarget.LaneHeader("Lane 14").testTag()).getUnclippedBoundsInRoot()
		assertTrue(lastHeader.right > 600.dp, "the last lane ends at ${lastHeader.right}")
	}

	@Test
	fun `A dated timeline wide enough for its lanes does not scroll sideways`() {
		showDatedPane(laneCount = 3, width = 900.dp)

		compose.onNodeWithTag(TIMELINE_HORIZONTAL_SCROLL_TAG).assertDoesNotExist()
	}

	private fun drawnRails(): List<String> = (1..6).map { "Lane $it" }.filter { lane ->
		compose.onAllNodesWithTag(HitTarget.LaneHeader(lane).testTag()).fetchSemanticsNodes().isNotEmpty()
	}

	@Test
	fun `A phone draws a rail for every lane while the lane filter is empty`() {
		showDatedPane(laneCount = 6, width = 360.dp, screen = phoneScreen)

		assertEquals((1..6).map { "Lane $it" }, drawnRails())
		compose.onNodeWithTag(HitTarget.LaneHeader("Lane 3").testTag())
			.assertContentDescriptionContains("Lane 3", substring = true)
			.assertWidthIsEqualTo(22.dp)
	}

	@Test
	fun `A phone with many lanes draws only the filtered rails`() {
		showDatedPane(laneCount = 6, width = 360.dp, screen = phoneScreen, laneFilter = { setOf("Lane 2", "Lane 5") })

		assertEquals(listOf("Lane 2", "Lane 5"), drawnRails())
		compose.onNodeWithTag(TIMELINE_HORIZONTAL_SCROLL_TAG).assertDoesNotExist()
	}

	/** Runs what it is given only when told to, so a test can hold a layout in progress. */
	private class HeldDispatcher : CoroutineDispatcher() {
		private val held = ArrayDeque<Runnable>()

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			held.addLast(block)
		}

		fun runNext() = held.removeFirst().run()

		fun runAll() {
			while (held.isNotEmpty()) runNext()
		}
	}

	@Test
	fun `A dated timeline keeps its last layout until the next is ready and never shows one it has moved past`() {
		val layouts = HeldDispatcher()
		var laneFilter by mutableStateOf(emptySet<String>())
		showDatedPane(laneCount = 3, width = 900.dp, laneFilter = { laneFilter }, layoutHost = TimelineLayoutHost(layouts))
		compose.waitForIdle()
		assertEquals(emptyList(), drawnRails())

		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()
		assertEquals(listOf("Lane 1", "Lane 2", "Lane 3"), drawnRails())

		laneFilter = setOf("Lane 2")
		compose.waitForIdle()
		laneFilter = setOf("Lane 3")
		compose.waitForIdle()
		assertEquals(listOf("Lane 1", "Lane 2", "Lane 3"), drawnRails())

		compose.runOnIdle { layouts.runNext() }
		compose.waitForIdle()
		assertEquals(listOf("Lane 1", "Lane 2", "Lane 3"), drawnRails())

		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()
		assertEquals(listOf("Lane 3"), drawnRails())
	}

	private fun ledgerTop(eventId: Int) =
		compose.onNodeWithTag(HitTarget.Ledger(eventId).testTag()).getUnclippedBoundsInRoot().top

	@Test
	fun `A dated timeline that returns draws its last layout, where it was left, while the next is computed`() {
		val layouts = HeldDispatcher()
		val lastLayout = LastTimelineLayout()
		var shown by mutableStateOf(true)
		showOverview(
			longDrawing(),
			screen = desktopScreen,
			size = DpSize(800.dp, 600.dp),
			shown = { shown },
			layoutHost = TimelineLayoutHost(layouts),
			lastLayout = lastLayout,
		)
		compose.onNodeWithTag(HitTarget.Ledger(30).testTag()).assertDoesNotExist()
		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()
		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performMouseInput {
			moveTo(center)
			scroll(5f)
		}
		compose.waitForIdle()
		val scrolled = ledgerTop(30)

		// As when an event opened from the list covers the overview, and is closed again.
		compose.runOnIdle { shown = false }
		compose.waitForIdle()
		compose.runOnIdle { shown = true }
		compose.waitForIdle()

		assertEquals(scrolled, ledgerTop(30))
		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()
		assertEquals(scrolled, ledgerTop(30))
	}

	@OptIn(ExperimentalSharedTransitionApi::class)
	@Test
	fun `A drawing scrolled down is where it was left when the timeline stack returns to it`() {
		val layouts = HeldDispatcher()
		val layoutHost = TimelineLayoutHost(layouts)
		val overview = longDrawing()
		val overviewChild = Child.Created<String, String>("overview", "overview")
		val stack = MutableValue(ChildStack(overviewChild))
		compose.setContent {
			CompositionLocalProvider(LocalScreenCharacteristic provides desktopScreen, LocalTimelineLayoutHost provides layoutHost) {
				Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
					SharedTransitionLayout {
						// The timeline's own stack and animation, with a stand-in for the event screen.
						AnimatedChildStack(stack = stack, animation = stackAnimation(animator = fade())) { child ->
							if (child.instance == "overview") {
								TimeLineOverviewUi(
									component = overview,
									scope = scope,
									showCreate = {},
									viewEvent = {},
									switcher = TimelineSwitcherActions.None,
									sharedTransitionScope = this@SharedTransitionLayout,
									animatedVisibilityScope = this,
								)
							} else {
								Text("Event")
							}
						}
					}
				}
			}
		}
		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()
		val atTop = ledgerTop(30)
		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performMouseInput {
			moveTo(center)
			scroll(5f)
		}
		compose.waitForIdle()
		val scrolled = ledgerTop(30)
		assertTrue(scrolled < atTop, "scrolled down")

		compose.runOnIdle { stack.value = ChildStack(Child.Created("event", "event"), backStack = listOf(overviewChild)) }
		compose.waitForIdle()
		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).assertDoesNotExist()
		compose.runOnIdle { stack.value = ChildStack(overviewChild) }
		compose.waitForIdle()
		compose.onNodeWithTag(HitTarget.Ledger(30).testTag()).assertDoesNotExist()
		compose.runOnIdle { layouts.runAll() }
		compose.waitForIdle()

		assertEquals(scrolled, ledgerTop(30))
	}

	@Test
	fun `Tapping a rail cap on a phone toggles its lane`() {
		val rows = listOf(
			datedRow(TimeLineEvent(id = 1, order = 0, title = "Fall", lane = "Alice"), year = 1, laneIndex = 0),
			datedRow(TimeLineEvent(id = 2, order = 1, title = "Tarts", lane = "Queen"), year = 2, laneIndex = 1),
		)
		val component = FakeOverview(rows, lanes = listOf(LaneCount("Alice", 1), LaneCount("Queen", 1)), mode = TimelineMode.DATED)
		showPhoneOverview(component)

		compose.onNodeWithTag(HitTarget.LaneHeader("Queen").testTag()).performClick()

		assertEquals(setOf("Queen"), component.state.value.laneFilter)
	}

	@Test
	fun `A manual timeline shows the list and no view picker`() {
		showOverview(FakeOverview(listOf(row(TimeLineEvent(id = 1, order = 0, title = "One")))))

		compose.onNodeWithTag(TIME_LINE_LIST_TAG).assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).assertDoesNotExist()
		compose.onNodeWithTag(TIME_LINE_DRAWING_MENU_TAG).assertDoesNotExist()
	}

	private fun showFab(overview: TimeLineOverview, rootFab: RootFabState = RootFabState()) {
		val timeline = mockk<TimeLine>(relaxed = true)
		every { timeline.stack } returns MutableValue(
			ChildStack(
				configuration = TimeLine.Config.TimeLineOverviewConfig(fakeProjectDef()),
				instance = TimeLine.Destination.TimeLineOverviewDestination(overview),
			)
		)
		every { timeline.eventDialog } returns eventDialog
		compose.setContent {
			CompositionLocalProvider(LocalRootFabState provides rootFab) { TimelineFab(timeline) }
		}
	}

	private val eventDialog = MutableValue<ChildSlot<TimeLine.EventDialogConfig, ViewTimeLineEvent>>(ChildSlot())

	@Test
	fun `The create FAB hides while an event is open over the drawing`() {
		val component = FakeOverview(listOf(row(TimeLineEvent(id = 7, order = 0, title = "Croquet"))))
		showFab(component)

		compose.runOnIdle {
			eventDialog.value = ChildSlot(
				Child.Created(TimeLine.EventDialogConfig(fakeProjectDef(), 7), mockk<ViewTimeLineEvent>(relaxed = true))
			)
		}
		compose.waitForIdle()

		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).assertDoesNotExist()
	}

	@Test
	fun `The create FAB shows while the timeline is at rest`() {
		val component = FakeOverview(listOf(row(TimeLineEvent(id = 7, order = 0, title = "Croquet"))))

		showFab(component)

		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).assertIsDisplayed()
	}

	@Test
	fun `The create FAB hides while scrolled down`() {
		val rootFab = RootFabState()
		showFab(FakeOverview(listOf(row(TimeLineEvent(id = 7, order = 0, title = "Croquet")))), rootFab)

		compose.runOnIdle { rootFab.scrolledDown = true }
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).assertDoesNotExist()

		compose.runOnIdle { rootFab.scrolledDown = false }
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).assertIsDisplayed()
	}

	private fun longLanedList() = FakeOverview(
		rows = (0 until 30).map { i ->
			val lane = if (i < 2) "Alice" else "Queen"
			row(TimeLineEvent(id = i, order = i, title = "Event $i", lane = lane), laneIndex = if (i < 2) 0 else 1)
		},
		lanes = listOf(LaneCount("Alice", 2), LaneCount("Queen", 28)),
	)

	private fun longDrawing() = FakeOverview(
		rows = (0 until 40).map { i -> datedRow(TimeLineEvent(id = i, order = i, title = "Event $i"), year = i.toLong()) },
		mode = TimelineMode.DATED,
	)

	@Test
	fun `Swiping the drawing down and back up reports it for the create FAB`() {
		val rootFab = RootFabState()
		showOverview(longDrawing(), screen = desktopScreen, size = DpSize(800.dp, 600.dp), rootFab = rootFab)

		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()
		assertTrue(rootFab.scrolledDown, "scrolled down")

		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performTouchInput { swipeDown() }
		compose.waitForIdle()
		assertEquals(false, rootFab.scrolledDown)
	}

	@Test
	fun `A drawing that loads after the first frame still reports scrolling for the create FAB`() {
		val component = FakeOverview(emptyList(), mode = TimelineMode.DATED)
		val rootFab = RootFabState()
		showOverview(component, screen = desktopScreen, size = DpSize(800.dp, 600.dp), rootFab = rootFab)
		compose.runOnIdle { component.load(longDrawing().state.value.rows!!) }
		compose.waitForIdle()

		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performMouseInput {
			moveTo(center)
			scroll(5f)
		}
		compose.waitForIdle()
		assertTrue(rootFab.scrolledDown, "scrolled down")

		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).performMouseInput {
			moveTo(center)
			scroll(-2f)
		}
		compose.waitForIdle()
		assertEquals(false, rootFab.scrolledDown)
	}

	@Test
	fun `Scrolling the list down and back up reports it for the create FAB`() {
		val rootFab = RootFabState()
		showOverview(longLanedList(), size = DpSize(800.dp, 600.dp), rootFab = rootFab)

		compose.onNodeWithTag(TIME_LINE_LIST_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()
		assertTrue(rootFab.scrolledDown, "scrolled down")

		compose.onNodeWithTag(TIME_LINE_LIST_TAG).performTouchInput { swipeDown() }
		compose.waitForIdle()
		assertEquals(false, rootFab.scrolledDown)
	}

	@Test
	fun `A filter that leaves the list short brings the collapsed strip back`() {
		val component = longLanedList()
		val rootFab = RootFabState()
		showOverview(component, size = DpSize(800.dp, 600.dp), rootFab = rootFab)
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_LANE_FILTER_TAG).assertIsNotDisplayed()

		compose.runOnIdle { component.setLaneFilter(setOf("Alice")) }
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_LIST_TAG).performTouchInput { swipeUp() }
		compose.waitForIdle()

		compose.onAllNodesWithTag(EVENT_CARD_TAG).assertCountEquals(2)
		compose.onNodeWithTag(TIME_LINE_LANE_FILTER_TAG).assertIsDisplayed()
		assertEquals(false, rootFab.scrolledDown)
	}

	@Test
	fun `A framed band shows a pill on the drawing that returns to everything`() {
		val component = FakeOverview(
			listOf(
				datedRow(TimeLineEvent(id = 7, order = 0, title = "The siege", lane = "Alice"), year = 1, endYear = 4, laneIndex = 0),
				datedRow(TimeLineEvent(id = 8, order = 1, title = "Croquet"), year = 6),
			),
			lanes = listOf(LaneCount("Alice", 1)),
			mode = TimelineMode.DATED,
		)
		showOverview(component, screen = desktopScreen)
		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).assertDoesNotExist()

		compose.runOnIdle { component.setFrame(TimelineFrame.Band(7)) }
		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).assertIsDisplayed()
		compose.onNode(hasText("THE SIEGE") and hasAnyAncestor(hasTestTag(TIME_LINE_FRAME_PILL_TAG)), useUnmergedTree = true).assertExists()

		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).performClick()

		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		compose.onNodeWithTag(TIME_LINE_FRAME_PILL_TAG).assertDoesNotExist()
	}

	private class RecordingSwitcher {
		val selected = mutableListOf<TimelineSelection>()
		var newRequests = 0
		val renamed = mutableListOf<String>()
		val deleted = mutableListOf<String>()
		var settingsOpened = 0
		val actions = TimelineSwitcherActions(
			onSelect = { selected += it },
			onNew = { newRequests++ },
			onRename = { renamed += it },
			onDelete = { deleted += it },
			onSettings = { settingsOpened++ },
		)
	}

	private val main = TimelineSummary(TimelineDef.DEFAULT, 3)
	private val backstory = TimelineSummary(TimelineDef(id = "backstory", name = "Before the evening"), 13)
	private val switcherRows = listOf(row(TimeLineEvent(id = 1, order = 0, title = "Croquet")))

	private fun switcherTitle(title: String) = hasText(title) and hasText("▾") and hasClickAction()

	@Test
	fun `With only main the masthead says Timeline with a chevron and opens the switcher`() {
		val switcher = RecordingSwitcher()
		showOverview(FakeOverview(switcherRows, timelines = listOf(main)), switcher = switcher.actions, screen = desktopScreen)

		compose.onNode(switcherTitle("Timeline")).assertIsDisplayed().performClick()
		compose.onNodeWithTag(timelineSwitcherRowTag(TimelineDef.MAIN_ID)).assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_NEW_TAG).performClick()

		assertEquals(1, switcher.newRequests)
	}

	@Test
	fun `With two timelines the masthead names the one shown`() {
		showOverview(
			FakeOverview(switcherRows, timelines = listOf(main, backstory), selection = TimelineSelection.Def("backstory")),
			screen = desktopScreen,
		)

		compose.onNode(switcherTitle("Before the evening")).assertIsDisplayed()
		compose.onNode(switcherTitle("Timeline")).assertDoesNotExist()
	}

	@Test
	fun `With two timelines main is called by its name`() {
		showOverview(FakeOverview(switcherRows, timelines = listOf(main, backstory)), screen = desktopScreen)

		compose.onNode(switcherTitle("Main")).assertIsDisplayed()
	}

	@Test
	fun `The switcher lists every timeline with its count and acts on the one shown`() {
		val switcher = RecordingSwitcher()
		showOverview(
			FakeOverview(switcherRows, timelines = listOf(main, backstory), selection = TimelineSelection.Def("backstory")),
			switcher = switcher.actions,
			screen = desktopScreen,
		)
		compose.onNode(switcherTitle("Before the evening")).performClick()

		compose.onNodeWithTag(timelineSwitcherRowTag("main")).assertTextContains("Main").assertTextContains("3")
		compose.onNodeWithTag(timelineSwitcherRowTag("backstory")).assertTextContains("13")
		compose.onNodeWithTag(TIME_LINE_SWITCHER_SETTINGS_TAG).assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_DELETE_TAG).assertTextContains("Delete “Before the evening”…")
		compose.onNodeWithTag(TIME_LINE_SWITCHER_RENAME_TAG).assertTextContains("Rename “Before the evening”…").performClick()

		assertEquals(listOf("backstory"), switcher.renamed)
	}

	@Test
	fun `Delete in the switcher asks about the timeline shown`() {
		val switcher = RecordingSwitcher()
		showOverview(
			FakeOverview(switcherRows, timelines = listOf(main, backstory), selection = TimelineSelection.Def("backstory")),
			switcher = switcher.actions,
		)
		compose.onNode(switcherTitle("Before the evening")).performClick()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_DELETE_TAG).performClick()

		assertEquals(listOf("backstory"), switcher.deleted)
	}

	@Test
	fun `Main cannot be deleted from the switcher`() {
		showOverview(FakeOverview(switcherRows, timelines = listOf(main, backstory)))
		compose.onNode(switcherTitle("Main")).performClick()

		compose.onNodeWithTag(TIME_LINE_SWITCHER_RENAME_TAG).assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_DELETE_TAG).assertDoesNotExist()
	}

	@Test
	fun `Settings opens from the switcher`() {
		val switcher = RecordingSwitcher()
		showOverview(FakeOverview(switcherRows, timelines = listOf(main)), switcher = switcher.actions)
		compose.onNode(switcherTitle("Timeline")).performClick()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_SETTINGS_TAG).performClick()

		assertEquals(1, switcher.settingsOpened)
	}

	@Test
	fun `The deleted bucket is in the switcher only while it holds events`() {
		val component = FakeOverview(switcherRows, timelines = listOf(main, backstory))
		showOverview(component)
		compose.onNode(switcherTitle("Main")).performClick()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_DELETED_TAG).assertDoesNotExist()
	}

	@Test
	fun `The deleted bucket is in the switcher with its count`() {
		val switcher = RecordingSwitcher()
		showOverview(FakeOverview(switcherRows, timelines = listOf(main), orphanCount = 2), switcher = switcher.actions)
		compose.onNode(switcherTitle("Timeline")).performClick()

		compose.onNodeWithTag(TIME_LINE_SWITCHER_DELETED_TAG)
			.assertTextContains("Deleted timeline")
			.assertTextContains("2")
			.performClick()

		assertEquals(listOf<TimelineSelection>(TimelineSelection.Orphans), switcher.selected)
	}

	@Test
	fun `Choosing a timeline in the switcher selects it and closes the menu`() {
		val switcher = RecordingSwitcher()
		showOverview(FakeOverview(switcherRows, timelines = listOf(main, backstory)), switcher = switcher.actions)
		compose.onNode(switcherTitle("Main")).performClick()
		compose.onNodeWithTag(timelineSwitcherRowTag("backstory")).performClick()
		compose.waitForIdle()

		assertEquals(listOf<TimelineSelection>(TimelineSelection.Def("backstory")), switcher.selected)
		compose.onNodeWithTag(timelineSwitcherRowTag("backstory")).assertDoesNotExist()
	}

	private fun orphansOverview() = FakeOverview(
		listOf(
			row(TimeLineEvent(id = 4, order = 0, title = "Made offline", timeline = "gone")),
			row(TimeLineEvent(id = 5, order = 1, title = "Also offline", timeline = "gone")),
		),
		timelines = listOf(main, backstory),
		selection = TimelineSelection.Orphans,
		orphanCount = 2,
	)

	@Test
	fun `The deleted bucket shows its notice over a list whose rows open their events`() {
		val viewed = mutableListOf<Int>()
		showOverview(orphansOverview(), viewEvent = { viewed += it }, screen = desktopScreen)

		compose.onNode(switcherTitle("Deleted timeline")).assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_DELETED_NOTICE_TAG).assertIsDisplayed()
		compose.onNodeWithText(
			"2 events were added on another device to a timeline that has since been deleted."
		).assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_DRAWING_TAG).assertDoesNotExist()
		compose.onNodeWithText("Also offline").performClick()

		assertEquals(listOf(5), viewed)
	}

	@Test
	fun `Move all to moves the deleted bucket to the timeline picked`() {
		val component = orphansOverview()
		showOverview(component, uiScope = CoroutineScope(Dispatchers.Unconfined))

		compose.onNodeWithText("Move all to…").performClick()
		compose.onNodeWithTag(timelineMoveAllTargetTag("backstory")).performClick()
		compose.waitForIdle()

		assertEquals(listOf("backstory"), component.orphansMovedTo)
	}

	@Test
	fun `Move all to reports the events it left behind`() {
		val component = orphansOverview()
		val left = mutableListOf<Int>()
		showOverview(component, uiScope = CoroutineScope(Dispatchers.Unconfined), onOrphansLeft = { left += it })

		compose.onNodeWithText("Move all to…").performClick()
		compose.onNodeWithTag(timelineMoveAllTargetTag("backstory")).performClick()
		compose.waitForIdle()
		assertEquals(emptyList(), left)

		component.orphansStaying = 1
		compose.onNodeWithText("Move all to…").performClick()
		compose.onNodeWithTag(timelineMoveAllTargetTag("backstory")).performClick()
		compose.waitForIdle()
		assertEquals(listOf(1), left)
	}

	@Test
	fun `Delete all asks before deleting the deleted bucket`() {
		val component = orphansOverview()
		showOverview(component, uiScope = CoroutineScope(Dispatchers.Unconfined))

		compose.onNodeWithText("Delete all").performClick()
		compose.waitForIdle()
		assertEquals(0, component.orphansDeleted)

		compose.onNodeWithText("Delete 2 events?").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_DELETED_DELETE_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(1, component.orphansDeleted)
	}

	@Test
	fun `A timeline has no deleted bucket notice`() {
		showOverview(FakeOverview(switcherRows, timelines = listOf(main), orphanCount = 2))

		compose.onNodeWithTag(TIME_LINE_DELETED_NOTICE_TAG).assertDoesNotExist()
	}

	@Test
	fun `The create FAB is hidden in the deleted bucket`() {
		showFab(orphansOverview())

		compose.onNodeWithTag(TIME_LINE_CREATE_TAG).assertDoesNotExist()
	}

	private val longName = TimelineSummary(
		TimelineDef(id = "scratch", name = "Scratch: what the keeper remembers of the wreck of the Alba"),
		2,
	)

	@Test
	fun `On a phone the switcher sits beside search and ellipsizes a long name`() {
		val switcher = RecordingSwitcher()
		showOverview(
			FakeOverview(switcherRows, timelines = listOf(main, longName), selection = TimelineSelection.Def("scratch")),
			switcher = switcher.actions,
			screen = phoneScreen,
			size = DpSize(360.dp, 760.dp),
		)

		compose.onNodeWithContentDescription("Search timeline").assertIsDisplayed()
		val title = compose.onNode(hasText(longName.def.name) and hasClickAction()).assertIsDisplayed()
		val layouts = mutableListOf<TextLayoutResult>()
		compose.onNode(hasText(longName.def.name) and !hasClickAction(), useUnmergedTree = true)
			.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
		assertTrue(layouts.single().hasVisualOverflow, "the name is cut short")
		assertEquals(1, layouts.single().lineCount)

		title.performClick()
		compose.onNodeWithTag(timelineSwitcherRowTag("main")).performClick()
		assertEquals(listOf<TimelineSelection>(TimelineSelection.Main), switcher.selected)
	}

	@Test
	fun `On a phone the switcher is hidden while searching`() {
		showPhoneOverview(FakeOverview(switcherRows, timelines = listOf(main)))
		compose.onNode(switcherTitle("Timeline")).assertIsDisplayed()

		compose.onNodeWithContentDescription("Search timeline").performClick()
		compose.waitForIdle()

		compose.onNodeWithTag(TIME_LINE_SEARCH_TAG).assertIsDisplayed()
		compose.onNode(switcherTitle("Timeline")).assertDoesNotExist()
	}

	@Test
	fun `On a phone with only main the switcher still opens`() {
		val switcher = RecordingSwitcher()
		showOverview(
			FakeOverview(switcherRows, timelines = listOf(main)),
			switcher = switcher.actions,
			screen = phoneScreen,
			size = DpSize(360.dp, 760.dp),
		)

		compose.onNode(switcherTitle("Timeline")).performClick()
		compose.onNodeWithTag(TIME_LINE_SWITCHER_NEW_TAG).performClick()

		assertEquals(1, switcher.newRequests)
	}
}
