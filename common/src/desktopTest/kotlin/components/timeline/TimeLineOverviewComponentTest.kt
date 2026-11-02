package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeLineOverviewComponentTest : TimelineComponentTestBase() {

	private fun newComponent() = TimeLineOverviewComponent(
		componentContext = context,
		projectDef = projectDef,
		addMenu = {},
		removeMenu = {},
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
}
