package timelinerepository

import BaseTest
import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineComponent
import getProjectDef
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineComponentTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var timelineRepo: TimeLineRepository
	lateinit var idRepo: IdRepository
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>
	lateinit var timelineRepoCollectCallback: CapturingSlot<FlowCollector<TimeLineContainer>>
	lateinit var storeTimelineCapture: CapturingSlot<TimeLineContainer>

	@Before
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()

		timelineRepo = mockk()
		idRepo = mockk()
		context = mockk()
		lifecycle = mockk()

		lifecycleCallbacks = mutableListOf()

		val testModule = module {
			single { timelineRepo } bind TimeLineRepository::class
			single { idRepo } bind IdRepository::class
		}
		setupKoin(testModule)

		every { context.lifecycle } returns lifecycle
		every {
			lifecycle.subscribe(capture(lifecycleCallbacks))
		} just Runs

		every { lifecycle.unsubscribe(any()) } just Runs

		timelineRepoCollectCallback = slot()
		val timelineFlow: MutableSharedFlow<TimeLineContainer> = mockk()
		coJustAwait { timelineFlow.collect(capture(timelineRepoCollectCallback)) }

		every { timelineRepo.timelineFlow } returns timelineFlow

		storeTimelineCapture = slot<TimeLineContainer>()
		every { timelineRepo.storeTimeline(capture(storeTimelineCapture)) } answers {
			scope.launch {
				timelineRepoCollectCallback.captured.emit(storeTimelineCapture.captured)
			}
		}
	}

	@Test
	fun `TimeLine Component onCreate`() = runTest {
		val component = TimeLineComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)

		// 1 from decompose, 2 from our component
		assertEquals(3, lifecycleCallbacks.size, "Three callbacks should be captured")

		// First callback is from decompose
		lifecycleCallbacks[1].onCreate()

		advanceUntilIdle()

		assertTrue("TimeLine repo flow should have been subscribed to") { timelineRepoCollectCallback.isCaptured }
	}

	@Test
	fun `Initial data from repo`() = runTest {
		val component = TimeLineComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)

		// First callback is from decompose
		lifecycleCallbacks[1].onCreate()

		advanceUntilIdle()

		assertNull(component.state.value.timeLine, "Timeline hasn't been emitted yet, should be null")

		val timeline = TimeLineContainer(
			events = fakeEvents()
		)

		// Mocked repo emits a timeline
		timelineRepoCollectCallback.captured.emit(timeline)

		advanceUntilIdle()

		// Component's state should now have that timeline
		assertEquals(timeline, component.state.value.timeLine, "Timeline did not propogate")
	}

	@Test
	fun `Create event`() = runTest {
		val id = 0
		every { idRepo.claimNextId() } returns id

		val component = TimeLineComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()
		val timeline = TimeLineContainer(events = emptyList())
		timelineRepoCollectCallback.captured.emit(timeline)
		advanceUntilIdle()

		val date = "date"
		val content = "content"
		val didCreate = component.createEvent(dateText = date, contentText = content)

		assertTrue("Component failed to create event") { didCreate }
		verify(exactly = 1) { timelineRepo.storeTimeline(any()) }
		advanceUntilIdle()

		val expected = TimeLineContainer(
			events = listOf(
				TimeLineEvent(
					id = id,
					date = date,
					content = content
				)
			)
		)

		// Our state should be updated after the repository stores it
		assertEquals(expected, component.state.value.timeLine, "Timeline did not propagate")
	}

	@Test
	fun `Update event`() = runTest {
		val component = TimeLineComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		val originalEvents = fakeEvents()
		val timeline = TimeLineContainer(events = originalEvents)
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

		val events = fakeEvents().toMutableList()
		events[0] = events.first().copy(
			date = date,
			content = content
		)
		val expected = TimeLineContainer(events = events)
		assertEquals(expected, component.state.value.timeLine, "Timeline did not propagate")

		// After the update, it should save back to repository
		verify(exactly = 1) { timelineRepo.storeTimeline(any()) }
	}
}