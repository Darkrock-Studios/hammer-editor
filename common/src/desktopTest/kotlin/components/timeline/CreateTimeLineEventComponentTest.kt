package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import repositories.timeline.TimeLineTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateTimeLineEventComponentTest : TimeLineTestBase() {

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Create event`() = runTest {
		val id = 0
		coEvery { idRepo.claimNextId() } returns id

		val originalEvents = listOf(
			TimeLineEvent(
				id = id,
				order = 0,
				date = "original date",
				content = "original content"
			)
		)
		val timeline = TimeLineContainer(originalEvents)
		coEvery { timelineRepo.loadTimeline() } returns timeline

		val date = "date"
		val content = "content"

		coEvery {
			timelineRepo.createEvent(
				content = any(),
				date = any()
			)
		} returns TimeLineEvent(
			id = id + 1,
			order = 1,
			date = date,
			content = content
		)

		val component = CreateTimeLineEventComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
		)
		val didCreate = component.createEvent(dateText = date, contentText = content)

		val eventContent = slot<String>()
		val eventDate = slot<String>()
		coVerify(exactly = 1) {
			timelineRepo.createEvent(
				capture(eventContent),
				capture(eventDate),
			)
		}

		assertTrue { didCreate }

		assertEquals(
			content,
			eventContent.captured,
			"Timeline did not pass the correct event data to be saved"
		)
		assertEquals(
			date,
			eventDate.captured,
			"Timeline did not pass the correct event data to be saved"
		)
	}
}