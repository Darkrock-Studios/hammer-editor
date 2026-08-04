package components.timeline

import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineComponent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals

class TimeLineComponentTest : ComponentTest() {

	private lateinit var timelineRepo: TimeLineRepository
	private lateinit var timelineFlow: MutableStateFlow<TimeLineContainer>

	private val event = TimeLineEvent(
		id = 1,
		order = 0,
		date = "original date",
		content = "original content",
		tags = emptySet(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		timelineFlow = MutableStateFlow(TimeLineContainer(listOf(event)))
		timelineRepo = mockk(relaxed = true)
		every { timelineRepo.timelineFlow } returns timelineFlow
		coEvery { timelineRepo.getTimelineEvent(event.id) } returns event

		val tagIndexService = mockk<TagIndexService>(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(mockk(relaxed = true))
		every { tagIndexService.getRankedTags(any<TaggedEntityType>(), any()) } returns emptyList()

		setupComponentKoin(module {
			single { timelineRepo } bind TimeLineRepository::class
			single { tagIndexService } bind TagIndexService::class
		})
	}

	private fun newComponent() = TimeLineComponent(
		componentContext = context,
		projectDef = projectDef,
		updateShouldClose = {},
		addMenu = {},
		removeMenu = {},
		onShowGlobalSearchForTag = {},
	)

	private fun viewEvent(component: TimeLineComponent) =
		(component.stack.value.active.instance as TimeLine.Destination.ViewEventDestination).component

	@Test
	fun `shouldConfirmClose is empty on the overview`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `shouldConfirmClose is empty when viewing an event but not editing`() =
		runTest(mainTestDispatcher) {
			val component = newComponent()
			context.resume()
			component.showViewEvent(event.id)
			advanceUntilIdle()

			assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
		}

	@Test
	fun `shouldConfirmClose flags an in-progress timeline event edit`() =
		runTest(mainTestDispatcher) {
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
}
