package components.timeline

import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import getProjectDef
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import repositories.timeline.fakeEvents
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineOverviewComponentTest : BaseTest() {

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

	private fun verifyEventSequence(events: List<TimeLineEvent>, vararg expectedIds: Int) {
		assertEquals(expectedIds.size, events.size, "Event list was not of expected size")

		events.forEachIndexed { index, event ->
			val info = if (event.id != expectedIds[index]) {
				val actual = events.map { it.id }.joinToString()
				val expected = expectedIds.joinToString()
				"expected: $expected\n  actual: $actual"
			} else {
				""
			}
			assertEquals(event.id, expectedIds[index], "Timeline events out of order\n$info\n")
		}
	}

	@Test
	fun `TimeLine Component onCreate`() = runTest {
		val component = TimeLineOverviewComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)

		// 1 from decompose, 1 from our component
		assertEquals(2, lifecycleCallbacks.size, "Three callbacks should be captured")

		// First callback is from decompose
		lifecycleCallbacks[1].onCreate()

		advanceUntilIdle()

		assertTrue("TimeLine repo flow should have been subscribed to") { timelineRepoCollectCallback.isCaptured }
	}

	@Test
	fun `Initial data from repo`() = runTest {
		val component = TimeLineOverviewComponent(
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

	suspend fun TestScope.defaultTestComponent(
		originalEvents: List<TimeLineEvent> = emptyList()
	): TimeLineOverviewComponent {
		val component = TimeLineOverviewComponent(
			componentContext = context,
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			addMenu = {},
			removeMenu = {}
		)
		lifecycleCallbacks[1].onCreate()
		advanceUntilIdle()

		val timeline = TimeLineContainer(events = originalEvents)
		timelineRepoCollectCallback.captured.emit(timeline)
		advanceUntilIdle()

		return component
	}

	@Test
	fun `Move event and store to disk`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val first = originalEvents.first()
		val moved = component.moveEvent(first, 4, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")
		assertEquals(3, newEvents.indexOf(first), "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 0, 4, 5, 6, 7, 8, 9)

		verify(exactly = 1) { timelineRepo.storeTimeline(any()) }
	}

	@Test
	fun `Move Fail 0-0`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val first = originalEvents.first()
		val moved = component.moveEvent(first, 0, false)
		assertFalse("Move should have failed") { moved }
	}

	@Test
	fun `Move event- 0 to 4 before`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val first = originalEvents.first()
		val moved = component.moveEvent(first, 4, false)
		assertTrue("Move timeline event failed") { moved }

		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")

		assertEquals(3, newEvents.indexOf(first), "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 0, 4, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 0 to 4 after`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val first = originalEvents.first()
		val moved = component.moveEvent(first, 4, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")
		assertEquals(4, newEvents.indexOf(first), "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 4, 0, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 4 to 1 before`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val four = originalEvents[4]
		val moved = component.moveEvent(four, 1, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")
		assertEquals(1, newEvents.indexOf(four), "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 0, 4, 1, 2, 3, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 4 to 1 after`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val four = originalEvents[4]
		val moved = component.moveEvent(four, 1, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")
		assertEquals(2, newEvents.indexOf(four), "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 0, 1, 4, 2, 3, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 0 to 10 before`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val zero = originalEvents[0]
		val moved = component.moveEvent(zero, 9, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")

		verifyEventSequence(newEvents, 1, 2, 3, 4, 5, 6, 7, 8, 0, 9)
		assertEquals(8, newEvents.indexOf(zero), "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 0 to 10 after`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val zero = originalEvents[0]
		val moved = component.moveEvent(zero, 9, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")

		verifyEventSequence(newEvents, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
		assertEquals(9, newEvents.indexOf(zero), "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 10 to 0 before`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val ten = originalEvents[9]
		val moved = component.moveEvent(ten, 0, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")

		verifyEventSequence(newEvents, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8)
		assertEquals(0, newEvents.indexOf(ten), "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 10 to 0 after`() = runTest {
		val originalEvents = fakeEvents()
		val component = defaultTestComponent(originalEvents)

		val ten = originalEvents[9]
		val moved = component.moveEvent(ten, 0, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = component.state.value.timeLine?.events ?: error("Timeline state was null!")

		verifyEventSequence(newEvents, 0, 9, 1, 2, 3, 4, 5, 6, 7, 8)
		assertEquals(1, newEvents.indexOf(ten), "Moved item was not at the correct position")
	}
}