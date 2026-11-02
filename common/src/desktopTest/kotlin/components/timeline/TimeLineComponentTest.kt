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
import kotlin.test.assertIs

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
}
