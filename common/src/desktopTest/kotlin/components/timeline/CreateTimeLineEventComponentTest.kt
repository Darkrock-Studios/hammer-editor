package components.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import repositories.timeline.TimeLineTestBase
import kotlin.test.assertEquals

class CreateTimeLineEventComponentTest : TimeLineTestBase() {

	@Test
	fun `Create event`() = runTest {
		val id = 0

		val date = "date"
		val content = "content"

		every { timelineRepo.validateTags(any()) } returns TimeLineEventError.NONE
		coEvery {
			timelineRepo.createEvent(
				content = any(),
				date = any(),
				tags = any(),
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
			onClose = {}
		)
		val result = component.createEvent(dateText = date, contentText = content)

		val eventContent = slot<String>()
		val eventDate = slot<String>()
		val eventTags = slot<Set<String>>()
		coVerify(exactly = 1) {
			timelineRepo.createEvent(
				content = capture(eventContent),
				date = capture(eventDate),
				tags = capture(eventTags),
			)
		}

		assertEquals(TimeLineEventError.NONE, result)

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
		assertEquals(
			emptySet(),
			eventTags.captured,
			"Tags should default to empty"
		)
	}
}