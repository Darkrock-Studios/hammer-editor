package repositories.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
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
	lateinit var globalSettingsRepo: GlobalSettingsRepository
	lateinit var globalSettingsUpdates: SharedFlow<GlobalSettings>
	lateinit var idRepo: IdRepository
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>
	lateinit var timelineRepoCollectCallback: CapturingSlot<FlowCollector<TimeLineContainer>>

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

		globalSettingsRepo = mockk()
		globalSettingsUpdates = mockk()
		coEvery { globalSettingsUpdates.collect(any()) } just Awaits
		every { globalSettingsRepo.globalSettingsUpdates } returns globalSettingsUpdates
		//every { globalSettingsRepo.globalSettingsUpdates getProperty "globalSettingsUpdates" } propertyType SharedFlow::class answers { globalSettingsUpdates }
		//every { globalSettingsRepo.globalSettingsUpdates } propertyType SharedFlow::class answers { globalSettingsUpdates }

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

		val testModule = module {
			//single { globalSettingsRepo } bind GlobalSettingsRepository::class
			scope<ProjectDefScope> {
				scoped { timelineRepo } bind TimeLineRepository::class
				scoped { idRepo } bind IdRepository::class
			}
		}
		setupKoin(testModule)
	}
}