package timelinerepository

import BaseTest
import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.timeline.TimeLineComponent
import getProjectDef
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineComponentTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var timelineRepo: TimeLineRepository
	lateinit var idRepo: IdRepository
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle

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

		val testModule = module {
			single { timelineRepo } bind TimeLineRepository::class
			single { idRepo } bind IdRepository::class
		}
		setupKoin(testModule)
	}

	@Test
	fun `TimeLine Component Setup`() = runTest {
		every { context.lifecycle } returns lifecycle
		val callbacks = mutableListOf<Lifecycle.Callbacks>()
		every {
			lifecycle.subscribe(capture(callbacks))
		} just Runs

		every { lifecycle.unsubscribe(any()) } just Runs

		val collectCallback = slot<FlowCollector<TimeLineContainer>>()
		val timelineFlow: MutableSharedFlow<TimeLineContainer> = mockk()
		coEvery { timelineFlow.collect(capture(collectCallback)) }

		every { timelineRepo.timelineFlow } returns timelineFlow

		val projDef = getProjectDef(PROJECT_EMPTY_NAME)

		val component = TimeLineComponent(
			componentContext = context,
			projectDef = projDef,
			addMenu = {},
			removeMenu = {}
		)

		// 1 from decompose, 2 from our component
		assertEquals(3, callbacks.size, "Three callbacks should be captured")

		// First callback is from decompose
		callbacks[1].onCreate()

		advanceUntilIdle()
	}

	@Test
	fun `Test Initial data from repo`() = runTest {
		every { context.lifecycle } returns lifecycle
		val callbacks = mutableListOf<Lifecycle.Callbacks>()
		every {
			lifecycle.subscribe(capture(callbacks))
		} just Runs

		every { lifecycle.unsubscribe(any()) } just Runs

		val collectCallback = slot<FlowCollector<TimeLineContainer>>()
		val timelineFlow: MutableSharedFlow<TimeLineContainer> = mockk()
		coEvery { timelineFlow.collect(capture(collectCallback)) }

		every { timelineRepo.timelineFlow } returns timelineFlow

		val projDef = getProjectDef(PROJECT_EMPTY_NAME)

		val component = TimeLineComponent(
			componentContext = context,
			projectDef = projDef,
			addMenu = {},
			removeMenu = {}
		)

		// 1 from decompose, 2 from our component
		assertEquals(3, callbacks.size, "Three callbacks should be captured")

		// First callback is from decompose
		callbacks[1].onCreate()

		advanceUntilIdle()

		assertNull(component.state.value.timeLine, "Timeline hasn't been emitted yet, should be null")

		val timeline = TimeLineContainer(
			events = fakeEvents()
		)

		// Mocked repo emits a timeline
		collectCallback.captured.emit(timeline)

		advanceUntilIdle()

		// Component's state should now have that timeline
		assertEquals(timeline, component.state.value.timeLine, "Timeline did not propogate")
	}
}