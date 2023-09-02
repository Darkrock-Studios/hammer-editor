package repositories.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var projectSynchronizer: ClientProjectSynchronizer

	@Before
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()
		projectSynchronizer = mockk()

		val testModule = module {
			single { projectSynchronizer }
		}
		setupKoin(testModule)
	}

	fun setupTimelne(
		projDef: ProjectDef = getProjectDef(PROJECT_EMPTY_NAME),
		events: List<TimeLineEvent> = fakeEvents()
	) {
		val dir = TimeLineRepositoryOkio.getTimelineDir(projDef).toOkioPath()
		ffs.createDirectories(dir)

		val timeline = TimeLineContainer(
			events = events
		)
		val text = json.encodeToString(timeline)

		println(text)

		val file = TimeLineRepositoryOkio.getTimelineFile(projDef).toOkioPath()
		ffs.write(file) {
			writeUtf8(text)
		}
	}

	@Test
	fun `Get Timeline Dir creates Dir`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdRepository>()

		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		).initialize()

		advanceUntilIdle()

		val timelineDir = TimeLineRepositoryOkio.getTimelineDir(projDef).toOkioPath()
		assertTrue("Timeline dir was not created") { ffs.exists(timelineDir) }
	}

	@Test
	fun `Get timeline when none exists`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdRepository>()

		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		).initialize()

		val timeline = repo.loadTimeline()
		assertTrue("Events should have been empty") { timeline.events.isEmpty() }
	}

	@Test
	fun `Collect initial timeline`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef)

		val idRepo = mockk<IdRepository>()
		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		repo.timelineFlow.take(1).collect { timeline ->
			collectedTimeline = timeline
		}

		advanceUntilIdle()

		assertNotNull(collectedTimeline, "Did not get initial timeline")

		collectedTimeline?.let {
			val events = fakeEvents()
			assertEquals(events.size, it.events.size, "Wrong number of events loaded")
		}
	}

	@Test
	fun `Update timeline`() = runTest {
		every { projectSynchronizer.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val idRepo = mockk<IdRepository>()
		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val content = "content"
		val date = "date"
		val id = 99
		val madeEvent = repo.createEvent(
			id = id,
			content = content,
			date = date
		)

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		collectedTimeline?.let {
			assertEquals(oldEvents.size + 1, it.events.size, "Updated events wrong size")
			assertEquals(madeEvent, it.events.last(), "Last even was not correct")
		}
	}
}