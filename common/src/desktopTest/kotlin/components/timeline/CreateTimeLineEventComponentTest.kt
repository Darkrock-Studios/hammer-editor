package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestComponentContext
import kotlin.test.assertEquals
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
}
