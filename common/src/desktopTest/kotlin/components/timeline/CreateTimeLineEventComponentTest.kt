package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import repositories.timeline.TimeLineTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateTimeLineEventComponentTest : TimeLineTestBase() {

	@Test
	fun `Create event`() = runTest {
		val id = 0
		every { idRepo.claimNextId() } returns id

		val originalEvents = listOf(
			TimeLineEvent(
				id = id,
				date = "original date",
				content = "original content"
			)
		)
		val timeline = TimeLineContainer(originalEvents)
		coEvery { timelineRepo.loadTimeline() } returns timeline

		val component = CreateTimeLineEventComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
		)

		val date = "date"
		val content = "content"
		val didCreate = component.createEvent(dateText = date, contentText = content)

		assertTrue { didCreate }

		val storedEvent = slot<TimeLineContainer>()
		verify(exactly = 1) { timelineRepo.storeTimeline(capture(storedEvent)) }

		val expected = TimeLineContainer(
			events = originalEvents + listOf(
				TimeLineEvent(
					id = id,
					date = date,
					content = content
				)
			)
		)
		assertEquals(
			expected,
			storedEvent.captured,
			"Timeline did not pass the correct event to be saved: ${storedEvent.captured}"
		)
	}
}