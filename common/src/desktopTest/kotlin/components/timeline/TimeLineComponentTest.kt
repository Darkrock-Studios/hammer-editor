package components.timeline

import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeLineComponentTest : TimelineComponentTestBase() {

	private lateinit var event: TimeLineEvent

	private suspend fun TestScope.givenEvent() {
		givenProject()
		val created = timelineService.createEvent(
			TimelineEventDraft(label = "original date", content = "original content")
		)
		event = (created as TimelineResult.Ok).event!!
		advanceUntilIdle()
	}

	private fun newComponent() = TimeLineComponent(
		componentContext = context,
		projectDef = projectDef,
		updateShouldClose = {},
		addMenu = {},
		removeMenu = {},
		onShowGlobalSearchForTag = {},
		onShowScene = {},
		onShowEntry = {},
	)

	private fun viewEvent(component: TimeLineComponent) =
		(component.stack.value.active.instance as TimeLine.Destination.ViewEventDestination).component

	@Test
	fun `shouldConfirmClose is empty on the overview`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `shouldConfirmClose is empty when viewing an event but not editing`() =
		runTest(mainTestDispatcher) {
			givenEvent()
			val component = newComponent()
			context.resume()
			component.showViewEvent(event.id)
			advanceUntilIdle()

			assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
		}

	@Test
	fun `shouldConfirmClose flags an in-progress timeline event edit`() =
		runTest(mainTestDispatcher) {
			givenEvent()
			val component = newComponent()
			context.resume()
			component.showViewEvent(event.id)
			advanceUntilIdle()

			val viewEvent = viewEvent(component)
			viewEvent.beginEdit()
			viewEvent.onEventTextChanged("a different body")
			advanceUntilIdle()

			assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())
		}

	@Test
	fun `discardUnsaved reverts a dirty timeline event edit`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showViewEvent(event.id)
		advanceUntilIdle()

		val viewEvent = viewEvent(component)
		viewEvent.beginEdit()
		viewEvent.onEventTextChanged("a different body")
		advanceUntilIdle()

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
		assertEquals(event.content, viewEvent.contentText.value)
	}

	@Test
	fun `discardUnsaved pops an open create event screen`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showCreateEvent()
		advanceUntilIdle()
		assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertIs<TimeLine.Destination.TimeLineOverviewDestination>(component.stack.value.active.instance)
		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `an event opened over the drawing keeps the overview and back closes it`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.showEventDialog(event.id)
		advanceUntilIdle()

		assertEquals(event.id, component.eventDialog.value.child?.instance?.eventId)
		assertIs<TimeLine.Destination.TimeLineOverviewDestination>(component.stack.value.active.instance)
		assertFalse(component.isAtRoot())

		component.onBack()
		advanceUntilIdle()

		assertNull(component.eventDialog.value.child)
		assertTrue(component.isAtRoot())
	}

	@Test
	fun `closing an event dialog with unsaved edits asks first`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showEventDialog(event.id)
		advanceUntilIdle()
		val dialog = component.eventDialog.value.child!!.instance
		dialog.beginEdit()
		dialog.onEventTextChanged("a different body")
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())

		component.closeEventDialog()
		advanceUntilIdle()

		assertTrue(dialog.state.value.confirmClose)
		assertNotNull(component.eventDialog.value.child)

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `leaving the timeline from an event dialog closes the dialog`() = runTest(mainTestDispatcher) {
		givenEvent()
		val searched = mutableListOf<String>()
		val component = TimeLineComponent(
			componentContext = context,
			projectDef = projectDef,
			updateShouldClose = {},
			addMenu = {},
			removeMenu = {},
			onShowGlobalSearchForTag = { searched += it },
			onShowScene = {},
			onShowEntry = {},
		)
		context.resume()
		component.showEventDialog(event.id)
		advanceUntilIdle()

		component.eventDialog.value.child!!.instance.showGlobalSearchForTag("storm")
		advanceUntilIdle()

		assertEquals(listOf("storm"), searched)
		assertNull(component.eventDialog.value.child)
	}
}
