package timelinerepository

import com.akuleshov7.ktoml.Toml
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest

abstract class TimeLineTestBase : BaseTest() {

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
}