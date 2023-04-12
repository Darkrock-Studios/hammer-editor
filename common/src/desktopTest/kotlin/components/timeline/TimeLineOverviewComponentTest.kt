package components.timeline

import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import getProjectDef
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineOverviewComponentTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var idRepo: IdRepository
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>
	lateinit var timelineRepoCollectCallback: CapturingSlot<FlowCollector<TimeLineContainer>>
	lateinit var timelineRepo: TimeLineRepository
	lateinit var globalSettingsRepo: GlobalSettingsRepository
	lateinit var globalSettingsFlow: SharedFlow<GlobalSettings>

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

		globalSettingsRepo = mockk()
		globalSettingsFlow = mockk()
		every { globalSettingsRepo.globalSettingsUpdates } returns globalSettingsFlow

		lifecycleCallbacks = mutableListOf()

		val testModule = module {
			single { timelineRepo } bind TimeLineRepository::class
			single { idRepo } bind IdRepository::class
			single { globalSettingsRepo }
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

		val eventContent = slot<String>()
		val eventDate = slot<String>()
		val eventId = slot<Int>()
		val eventOrder = slot<Int>()
		coEvery {
			timelineRepo.createEvent(
				capture(eventContent),
				capture(eventDate),
				capture(eventId),
				capture(eventOrder),
			)
		} coAnswers {
			val event = TimeLineEvent(
				id = 0,
				order = 0,
				date = eventDate.captured,
				content = eventContent.captured
			)
			val timeline = TimeLineContainer(
				events = listOf(event)
			)

			scope.launch {
				timelineRepoCollectCallback.captured.emit(timeline)
			}

			event
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

	suspend fun TestScope.defaultTestComponent(
		originalEvents: List<TimeLineEvent> = emptyList()
	): TimeLineOverviewComponent {
		coEvery { globalSettingsFlow.collect(any()) } just Awaits

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
}