package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.timeline.ViewTimeLineEventComponent
import getProjectDef
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import repositories.timeline.TimeLineTestBase
import repositories.timeline.fakeEvents
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ViewTimeLineEventComponentTest : TimeLineTestBase() {
	@Test
	fun `Update event`() = runTest {
		val eventId = 0

		val originalEvents = fakeEvents()
		val timeline = TimeLineContainer(originalEvents)
		coEvery { timelineRepo.loadTimeline() } returns timeline

		val component = ViewTimeLineEventComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			eventId = eventId
		)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		timelineRepoCollectCallback.captured.emit(timeline)
		advanceUntilIdle()

		val event = originalEvents.first()
		val date = "updated date"
		val content = "updated content"
		val updatedEvent = event.copy(
			date = date,
			content = content
		)
		component.updateEvent(updatedEvent)
		advanceUntilIdle()

		assertEquals(updatedEvent, component.state.value.event, "Timeline did not propagate")

		// After the update, it should save back to repository
		verify(exactly = 1) { timelineRepo.storeTimeline(any()) }
	}
}