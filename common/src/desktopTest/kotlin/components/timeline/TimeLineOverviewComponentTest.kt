package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimeLineOverviewComponentTest : TimelineComponentTestBase() {

	private val shownScenes = mutableListOf<SceneItem>()
	private val shownEntries = mutableListOf<EntryDef>()

	private fun newComponent() = TimeLineOverviewComponent(
		componentContext = context,
		projectDef = projectDef,
		addMenu = {},
		removeMenu = {},
		onShowScene = { shownScenes += it },
		onShowEntry = { shownEntries += it },
	)

	@Test
	fun `rows are empty until the timeline loads, then follow it`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		assertNull(component.state.value.rows)

		context.resume()
		advanceUntilIdle()
		assertEquals(emptyList(), component.state.value.rows)

		timelineService.createEvent(TimelineEventDraft(content = "Rain falls"))
		advanceUntilIdle()

		assertEquals(listOf("Rain falls"), component.state.value.rows?.map { it.title })
	}

	@Test
	fun `rows resolve titles, subtitles and lane indices`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.placeScene(4, null, null, lane = "Alice", milestone = true)
		timelineService.placeEntry(TEA_PARTY, null, null, lane = "Hatter", milestone = false)
		timelineService.createEvent(TimelineEventDraft(title = "Storm", label = "Night", lane = "Alice"))
		timelineService.createEvent(TimelineEventDraft(content = "Rain falls"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val rows = component.state.value.rows!!
		assertEquals(listOf("Scene ID 4", "Tea Party", "Storm", "Rain falls"), rows.map { it.title })
		assertEquals(listOf(TimelineKind.SCENE, TimelineKind.ENTRY, TimelineKind.EVENT, TimelineKind.EVENT), rows.map { it.event.kind })
		assertEquals(listOf("Chapter ID 2", null, null, null), rows.map { it.subtitle })
		assertEquals(listOf(0, 1, 0, null), rows.map { it.laneIndex })
		assertEquals(listOf("", "", "Night", ""), rows.map { it.whenText })
		assertTrue(rows.none { it.subjectMissing })
		assertEquals(listOf(LaneCount("Alice", 2), LaneCount("Hatter", 1)), component.state.value.lanes)
		assertEquals(TimelineMode.MANUAL, component.state.value.mode)
	}

	@Test
	fun `lane indices follow the lane order before the alphabet`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition { it.copy(laneOrder = listOf("Queen")) }
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("Queen", "Alice"), component.state.value.lanes.map { it.lane })
		assertEquals(listOf(0, 1), component.state.value.rows!!.map { it.laneIndex })
	}

	@Test
	fun `a placement whose subject is gone keeps its cached title`() = runTest(mainTestDispatcher) {
		givenProject()
		timeLineRepository.createEvent(
			TimelineEventDraft(kind = TimelineKind.SCENE, subject = 999, cachedTitle = "Gone scene")
		)

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val row = component.state.value.rows!!.single()
		assertTrue(row.subjectMissing)
		assertEquals("Gone scene", row.title)
	}

	@Test
	fun `toggling a lane filters by it and toggling again clears it`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.toggleLane("Queen")
		assertEquals(setOf("Queen"), component.state.value.laneFilter)
		component.toggleLane("Alice")
		assertEquals(setOf("Queen", "Alice"), component.state.value.laneFilter)
		component.toggleLane("Queen")
		assertEquals(setOf("Alice"), component.state.value.laneFilter)
		component.setLaneFilter(emptySet())
		assertEquals(emptySet(), component.state.value.laneFilter)
	}

	@Test
	fun `a lane that leaves the timeline leaves the filter`() = runTest(mainTestDispatcher) {
		givenProject()
		val croquet = (timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen")) as TimelineResult.Ok).event!!

		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.toggleLane("Queen")

		timelineService.setLane(croquet.id, null)
		advanceUntilIdle()

		assertEquals(emptySet(), component.state.value.laneFilter)
	}

	@Test
	fun `moving is refused on a dated timeline`() = runTest(mainTestDispatcher) {
		givenProject()
		val first = (timelineService.createEvent(TimelineEventDraft(title = "One")) as TimelineResult.Ok).event!!
		timelineService.createEvent(TimelineEventDraft(title = "Two"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertTrue(component.moveEvent(first, 1, after = true))

		timelineService.switchToDated(CalendarDef())
		advanceUntilIdle()

		assertEquals(TimelineMode.DATED, component.state.value.mode)
		assertFalse(component.moveEvent(first, 0, after = false))
		assertTrue(component.state.value.rows!!.all { it.start != null }, "Dated rows carry their start")
	}

	@Test
	fun `dated rows read their dates and undated legacy events are listed`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "One", label = "Dawn"))
		timelineService.createEvent(TimelineEventDraft(title = "Two"))
		timelineService.switchToDated(CalendarDef(epochName = "Landing"))
		val legacy = timeLineRepository.createEvent(TimelineEventDraft(title = "Legacy"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = component.state.value
		assertEquals(CalendarDef(epochName = "Landing"), state.calendar)
		assertEquals(
			listOf(StoryDate(mapOf("year" to 0L)), StoryDate(mapOf("year" to 1L)), null),
			state.rows!!.map { it.start },
		)
		assertEquals(listOf(CalendarDef(epochName = "Landing"), CalendarDef(epochName = "Landing"), null), state.rows.map { it.calendar })
		assertEquals(listOf("Dawn", "", ""), state.rows.map { it.whenText }, "The label stays as the fallback")
		assertEquals(listOf(legacy.id), state.undatedIds)
	}

	@Test
	fun `the drawing starts in sequence and follows the chosen view`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineView.SEQUENCE, component.state.value.view)

		component.setView(TimelineView.TO_SCALE)

		assertEquals(TimelineView.TO_SCALE, component.state.value.view)
	}

	private fun TimelineResult.event(): TimeLineEvent = (this as TimelineResult.Ok).event!!

	@Test
	fun `selecting an event and selecting it again clears the selection`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val rain = timelineService.createEvent(TimelineEventDraft(title = "Rain")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.select(storm.id)
		assertEquals(storm.id, component.state.value.selectedId)
		component.select(rain.id)
		assertEquals(rain.id, component.state.value.selectedId)
		component.select(rain.id)
		assertNull(component.state.value.selectedId)
		component.select(storm.id)
		component.select(null)
		assertNull(component.state.value.selectedId)
	}

	@Test
	fun `a deleted selection is cleared`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.select(storm.id)
		advanceUntilIdle()

		timeLineRepository.deleteEvent(storm)
		advanceUntilIdle()

		assertNull(component.state.value.selectedId)
		assertNull(component.state.value.selectionSummary)
	}

	@Test
	fun `framing a band survives recreation and back returns to everything`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", lane = "Keep")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		assertFalse(context.back(), "Nothing to go back from while everything is shown")

		component.setFrame(TimelineFrame.Band(storm.id))
		assertEquals(TimelineFrame.Band(storm.id), component.state.value.frame)

		val restored = TimeLineOverviewComponent(context.saveAndRecreate(), projectDef, {}, {}, {}, {})
		assertEquals(TimelineFrame.Band(storm.id), restored.state.value.frame)

		assertTrue(context.back())
		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		assertFalse(context.back())
	}

	@Test
	fun `a framed band that leaves the timeline returns to everything`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		timelineService.createEvent(TimelineEventDraft(title = "Rain"))
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.setFrame(TimelineFrame.Band(storm.id))

		timeLineRepository.deleteEvent(storm)
		advanceUntilIdle()

		assertEquals(TimelineFrame.Everything, component.state.value.frame)
	}

	@Test
	fun `the story frame needs a placed scene`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Storm"))
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(component.state.value.storyFrameAvailable)
		component.setFrame(TimelineFrame.Story)
		assertEquals(TimelineFrame.Everything, component.state.value.frame)

		timelineService.placeScene(4, StoryDate(mapOf("year" to 3L)), null, lane = null, milestone = false)
		advanceUntilIdle()

		assertTrue(component.state.value.storyFrameAvailable)
		component.setFrame(TimelineFrame.Story)
		assertEquals(TimelineFrame.Story, component.state.value.frame)
	}

	@Test
	fun `the summary of a placed scene lists its cast, people first`() = runTest(mainTestDispatcher) {
		givenProject()
		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(TEA_PARTY, WONDERLAND, ALICE)), sceneId = 4)
		val placed = timelineService.placeScene(4, null, null, lane = "Alice", milestone = false).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.select(placed.id)
		advanceUntilIdle()

		val summary = component.state.value.selectionSummary!!
		assertEquals(placed.id, summary.row.event.id)
		assertEquals("Scene ID 4", summary.row.title)
		assertEquals(listOf(ALICE, WONDERLAND, TEA_PARTY), summary.references.map { it.id })
		assertNull(summary.excerpt)
	}

	@Test
	fun `the summary of a plain event orders its references people, places, then scenes`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(
			TimelineEventDraft(title = "Storm", references = linkedSetOf(4, WONDERLAND, ALICE))
		).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.select(storm.id)
		advanceUntilIdle()

		assertEquals(listOf(ALICE, WONDERLAND, 4), component.state.value.selectionSummary!!.references.map { it.id })
	}

	@Test
	fun `the summary of a placed entry shows its opening text`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.select(placed.id)
		advanceUntilIdle()

		val summary = component.state.value.selectionSummary!!
		assertEquals("The Hatter pours tea.", summary.excerpt)
		assertEquals(emptyList(), summary.references)
	}

	@Test
	fun `a change elsewhere on the timeline keeps the selection's summary`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = linkedSetOf(ALICE))).event()
		val rain = timelineService.createEvent(TimelineEventDraft(title = "Rain")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.select(storm.id)
		advanceUntilIdle()
		val summary = assertNotNull(component.state.value.selectionSummary)

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(rain.id)!!.copy(title = "Hail"))
		advanceUntilIdle()

		assertEquals("Hail", component.state.value.rows!!.first { it.event.id == rain.id }.title)
		assertSame(summary, component.state.value.selectionSummary)
	}

	@Test
	fun `a change to the selected event refreshes its summary`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = linkedSetOf(ALICE))).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.select(storm.id)
		advanceUntilIdle()

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(storm.id)!!.copy(references = setOf(WONDERLAND, ALICE)))
		advanceUntilIdle()

		assertEquals(listOf(ALICE, WONDERLAND), component.state.value.selectionSummary!!.references.map { it.id })
	}

	@Test
	fun `a reference in the summary opens its entry or scene`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.openReference(catalogItem(ALICE))
		component.openReference(catalogItem(4))
		advanceUntilIdle()

		assertEquals(listOf(ALICE), shownEntries.map { it.id })
		assertEquals(listOf(4), shownScenes.map { it.id })
	}
}
