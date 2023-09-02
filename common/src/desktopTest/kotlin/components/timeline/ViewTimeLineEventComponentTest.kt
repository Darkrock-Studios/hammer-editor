package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import repositories.timeline.TimeLineTestBase
import repositories.timeline.fakeEvents
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewTimeLineEventComponentTest : TimeLineTestBase() {

	@Test
	fun `Update event`() = runTest {
		coEvery { timelineRepo.updateEvent(any()) } returns true

		val eventId = 0

		val originalEvents = fakeEvents()
		val timeline = TimeLineContainer(originalEvents)
		coEvery { timelineRepo.loadTimeline() } returns timeline

		val component = ViewTimeLineEventComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			eventId = eventId,
			closeEvent = {},
			addMenu = {},
			removeMenu = {},
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
		val success = component.updateEvent(updatedEvent)
		assertTrue(success, "Update event failed")

		// After the update, it should save back to repository
		coVerify(exactly = 1) { timelineRepo.updateEvent(updatedEvent) }
	}
}