package repositories.timeline

import PROJECT_EMPTY_NAME
import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.*
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.MAX_TAG_SIZE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var syncDataRepository: SyncDataRepository
	lateinit var datasource: TimeLineDatasource

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		syncDataRepository = mockk()
		datasource = TimeLineDatasource(ffs, toml)

		val testModule = module {
			single { syncDataRepository }
		}
		setupKoin(testModule)
	}

	private fun setupTimelne(
		projDef: ProjectDef = getProjectDef(PROJECT_EMPTY_NAME),
		events: List<TimeLineEvent> = fakeEvents()
	) {
		val dir = TimeLineDatasource.getTimelineDir(projDef).toOkioPath()
		ffs.createDirectories(dir)

		val timeline = TimeLineContainer(
			events = events
		)
		val text = toml.encodeToString(timeline)

		println(text)

		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.write(file) {
			writeUtf8(text)
		}
	}

	@Test
	fun `Get Timeline Dir creates Dir`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdAllocator>()

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		advanceUntilIdle()

		val timelineDir = TimeLineDatasource.getTimelineDir(projDef).toOkioPath()
		assertTrue("Timeline dir was not created") { ffs.exists(timelineDir) }
	}

	@Test
	fun `Get timeline when none exists`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val idRepo = mockk<IdAllocator>()

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		val timeline = repo.loadTimeline()
		assertTrue("Events should have been empty") { timeline.events.isEmpty() }
	}

	@Test
	fun `Collect initial timeline`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef)

		val idRepo = mockk<IdAllocator>()
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
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
	fun `Update timeline with new event`() = runTest {
		every { syncDataRepository.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val idRepo = mockk<IdAllocator>()
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
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

		// Check the filesystem
		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		Assertions.assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(
			collectedTimeline!!.events.size,
			loadedContainer.events.size,
			"Updated events wrong size"
		)
		assertEquals(madeEvent, loadedContainer.events.last())
	}

	@Test
	fun `Update timeline with updated event`() = runTest {
		every { syncDataRepository.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()

		var collectedTimeline: TimeLineContainer? = null
		val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
			repo.timelineFlow.take(2).collect { timeline ->
				collectedTimeline = timeline
			}
		}

		val updatedEvent = oldEvents[2].copy(
			date = "Updated Name",
			content = "Updated Content",
		)

		val updated = repo.updateEvent(event = updatedEvent)
		assertTrue("Event was not updated") { updated }

		advanceUntilIdle()

		collectJob.join()

		assertNotNull(collectedTimeline, "collectedTimeline was not set")

		// Check the memory cache
		collectedTimeline?.let {
			assertEquals(oldEvents.size, it.events.size, "Updated events wrong size")
			assertEquals(
				updatedEvent,
				it.events.find { it.id == updatedEvent.id },
				"Updated even was not correct"
			)
		}

		// Check the filesystem
		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		Assertions.assertTrue(ffs.exists(filePath))

		val loadedContainer: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(updatedEvent, loadedContainer.events.find { it.id == updatedEvent.id })
	}

	@Test
	fun `Create event with tags persists cleaned tags`() = runTest {
		every { syncDataRepository.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef, emptyList())

		val idRepo = mockk<IdAllocator>()
		coEvery { idRepo.claimNextId() } returns 42

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()

		val created = repo.createEvent(
			content = "An event",
			date = "Year 1",
			tags = setOf("  alpha  ", "#beta", "alpha", "with space", ""),
		)

		assertEquals(setOf("alpha", "beta"), created.tags)

		advanceUntilIdle()

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		val loaded: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(setOf("alpha", "beta"), loaded.events.find { it.id == 42 }?.tags)
	}

	@Test
	fun `Update event with tags persists cleaned tags`() = runTest {
		every { syncDataRepository.isServerSynchronized() } returns false

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		val oldEvents = fakeEvents()
		setupTimelne(projDef, oldEvents)

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = mockk(),
			datasource = datasource,
		).initialize()

		val updatedEvent = oldEvents[2].copy(
			tags = setOf("  alpha  ", "#beta", "with space"),
		)
		assertTrue("Event was not updated") { repo.updateEvent(event = updatedEvent) }

		advanceUntilIdle()

		val filePath = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		val loaded: TimeLineContainer = ffs.readToml(filePath, toml)
		assertEquals(setOf("alpha", "beta"), loaded.events.find { it.id == updatedEvent.id }?.tags)
	}

	@Test
	fun `Validate tags rejects tag exceeding MAX_TAG_SIZE`() {
		val repo = TimeLineRepository(
			projectDef = getProjectDef(PROJECT_EMPTY_NAME),
			idAllocator = mockk(),
			datasource = datasource,
		)

		assertEquals(
			TimeLineEventError.TAG_TOO_LONG,
			repo.validateTags(setOf("x".repeat(MAX_TAG_SIZE + 1))),
		)
		assertEquals(
			TimeLineEventError.NONE,
			repo.validateTags(setOf("x".repeat(MAX_TAG_SIZE))),
		)
	}

	@Test
	fun `eventContentChangedFlow emits on create update and delete`() = runTest {
		every { syncDataRepository.isServerSynchronized() } returns false
		coEvery { syncDataRepository.recordIdDeletion(any()) } returns Unit

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimelne(projDef, fakeEvents())

		val idRepo = mockk<IdAllocator>()
		coEvery { idRepo.claimNextId() } returns 99

		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idRepo,
			datasource = datasource,
		).initialize()
		advanceUntilIdle()

		repo.eventContentChangedFlow.test {
			val created = repo.createEvent(content = "c", date = null)
			awaitItem()

			repo.updateEvent(event = created.copy(content = "c2"))
			awaitItem()

			repo.deleteEvent(event = created)
			awaitItem()
		}
	}
}