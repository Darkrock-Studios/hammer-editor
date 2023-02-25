package repositories.timeline

import PROJECT_EMPTY_NAME
import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json

	@Before
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()

		setupKoin()
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
		)

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
		)

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
		)

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
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef)

		val idRepo = mockk<IdRepository>()
		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		)

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val oldEvents = fakeEvents()
		val updatedEvents = oldEvents.toMutableList()
		val newEvent = TimeLineEvent(
			id = 99,
			date = "date",
			content = "content"
		)
		updatedEvents.add(newEvent)
		val updatedTimeline = TimeLineContainer(updatedEvents)
		repo.storeTimeline(updatedTimeline)

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		collectedTimeline?.let {
			assertEquals(oldEvents.size + 1, it.events.size, "Updated events wrong size")
			assertEquals(newEvent, it.events.last(), "Last even was not correct")
		}
	}

	@Test
	fun `Remove all events from timeline`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef)

		val idRepo = mockk<IdRepository>()
		val repo = TimeLineRepositoryOkio(
			projectDef = projDef,
			idRepository = idRepo,
			fileSystem = ffs,
			json = json
		)

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val updatedTimeline = TimeLineContainer(emptyList())
		repo.storeTimeline(updatedTimeline)

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		collectedTimeline?.let {
			assertEquals(0, it.events.size, "Updated events was not empty")
		}
	}
}