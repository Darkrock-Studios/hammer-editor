package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateTimeLineEventComponentTest : TimelineComponentTestBase() {

	private var closed = 0

	private fun newComponent(ctx: TestComponentContext = context) = CreateTimeLineEventComponent(
		componentContext = ctx,
		projectDef = projectDef,
		onClose = { closed++ },
	)

	@Test
	fun `creating an event stores every field of the draft`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setTitle("  Storm  ")
		component.setLane("Alice")
		component.setMilestone(true)
		component.addReference(catalogItem(ALICE))
		component.addReference(catalogItem(4))
		component.onContentChanged("Thunder")
		val result = component.create(label = " Night ", content = "Thunder", tags = setOf("weather"))

		assertEquals(TimeLineEventError.NONE, result)
		val stored = timeLineRepository.currentEvents().single()
		assertEquals(TimelineKind.EVENT, stored.kind)
		assertEquals("Storm", stored.title)
		assertEquals("Night", stored.label)
		assertEquals("Thunder", stored.content)
		assertEquals(setOf("weather"), stored.tags)
		assertEquals(setOf(ALICE, 4), stored.references)
		assertEquals("Alice", stored.lane)
		assertTrue(stored.milestone)

		component.closeCreation()
		assertEquals(1, closed, "A created draft is cleared, so closing needs no confirmation")
	}

	@Test
	fun `switching kind clears the picked subject`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.SCENE)
		component.setSubject(catalogItem(4))
		assertEquals(4, component.state.value.subject?.id)

		component.setKind(TimelineKind.ENTRY)

		assertEquals(TimelineKind.ENTRY, component.state.value.kind)
		assertNull(component.state.value.subject)
	}

	@Test
	fun `creating with the scene kind places the scene`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.SCENE)
		component.setSubject(catalogItem(4))
		component.setLane("Alice")
		component.setMilestone(true)
		val result = component.create(label = "Tea time", content = "", tags = emptySet())

		assertEquals(TimeLineEventError.NONE, result)
		val stored = timeLineRepository.currentEvents().single()
		assertEquals(TimelineKind.SCENE, stored.kind)
		assertEquals(4, stored.subject)
		assertEquals("Scene ID 4", stored.cachedTitle)
		assertEquals("Alice", stored.lane)
		assertTrue(stored.milestone)
		assertEquals("Tea time", stored.label)
	}

	@Test
	fun `a placement needs a subject`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.SCENE)

		assertEquals(TimeLineEventError.SUBJECT_REQUIRED, component.create(null, "", emptySet()))
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `only an EVENT entry can be placed`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.ENTRY)
		component.setSubject(catalogItem(ALICE))
		assertEquals(TimeLineEventError.SUBJECT_WRONG_TYPE, component.create(null, "", emptySet()))

		component.setSubject(catalogItem(TEA_PARTY))
		assertEquals(TimeLineEventError.NONE, component.create(null, "", emptySet()))
		assertEquals(TEA_PARTY, timeLineRepository.currentEvents().single().subject)
	}

	@Test
	fun `lane suggestions come from the lanes in use`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))
		timelineService.createEvent(TimelineEventDraft(title = "Shrink", lane = "Alice's size"))
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("Alice", "Alice's size", "Queen"), component.suggestLanes(""))
		assertEquals(listOf("Alice", "Alice's size"), component.suggestLanes("al"))
		assertEquals(listOf("Alice's size"), component.suggestLanes("alice"))
		assertEquals(listOf("Queen"), component.suggestLanes("ee"))
	}

	@Test
	fun `the draft survives process death`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.SCENE)
		component.setSubject(catalogItem(4))
		component.setLane("Alice")
		component.setMilestone(true)

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		val state = restored.state.value
		assertEquals(TimelineKind.SCENE, state.kind)
		assertEquals(4, state.subject?.id)
		assertEquals("Alice", state.lane)
		assertTrue(state.milestone)
	}

	@Test
	fun `a dated timeline needs a start`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineMode.DATED, component.state.value.mode)
		assertEquals(CalendarDef(), component.state.value.calendar)
		assertFalse(component.state.value.canCreate)

		assertEquals(TimeLineEventError.DATE_REQUIRED, component.create(null, "Rain", emptySet()))
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `on a dated timeline the start and end reach the stored event`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val start = StoryDate(mapOf("year" to 12L, "day" to 40L))
		val end = StoryDate(mapOf("year" to 12L, "day" to 41L))
		component.setStart(start)
		component.setEnd(end)
		assertTrue(component.state.value.canCreate)
		assertEquals(TimeLineEventError.NONE, component.create(null, "Rain", emptySet()))

		component.setKind(TimelineKind.SCENE)
		component.setSubject(catalogItem(4))
		component.setStart(start)
		assertEquals(TimeLineEventError.NONE, component.create("Tea time", "", emptySet()))

		val (event, scene) = timeLineRepository.currentEvents().sortedBy { it.id }
		assertEquals(start, event.start)
		assertEquals(end, event.end)
		assertEquals(start, scene.start)
		assertNull(scene.end)
		assertEquals("Tea time", scene.label)
		assertNull(component.state.value.start, "A created draft is cleared")
	}

	@Test
	fun `on a manual timeline the drafted dates are left off`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setStart(StoryDate(mapOf("year" to 1L)))
		assertEquals(TimeLineEventError.NONE, component.create("Dawn", "Rain", emptySet()))

		val stored = timeLineRepository.currentEvents().single()
		assertNull(stored.start)
		assertEquals("Dawn", stored.label)
	}

	@Test
	fun `a placement with a label too long is not placed at all`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.SCENE)
		component.setSubject(catalogItem(4))
		val result = component.create("x".repeat(TimelineLimits.MAX_LABEL + 1), "", emptySet())

		assertEquals(TimeLineEventError.LABEL_TOO_LONG, result)
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `an entry placed with a label is one event with that label`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setKind(TimelineKind.ENTRY)
		component.setSubject(catalogItem(TEA_PARTY))
		val result = component.create(label = " Half past six ", content = "", tags = emptySet())

		assertEquals(TimeLineEventError.NONE, result)
		val stored = timeLineRepository.currentEvents().single()
		assertEquals(TimelineKind.ENTRY, stored.kind)
		assertEquals(TEA_PARTY, stored.subject)
		assertEquals("Half past six", stored.label)
	}
}
