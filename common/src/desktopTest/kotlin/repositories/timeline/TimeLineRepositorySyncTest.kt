package repositories.timeline

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositorySyncTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: TimeLineDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		syncJournal = mockk()
		datasource = TimeLineDatasource(ffs, toml)
		setupKoin(module { single { syncJournal } })
	}

	private fun setupTimeline(projDef: ProjectDef, events: List<TimeLineEvent>) {
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		ffs.createDirectories(file.parent!!)
		ffs.write(file) { writeUtf8(toml.encodeToString(TimeLineContainer.serializer(), TimeLineContainer(events))) }
	}

	private fun TestScope.initializedRepo(projDef: ProjectDef, idAllocator: IdAllocator = mockk()): TimeLineRepository {
		val repo = TimeLineRepository(
			projectDef = projDef,
			idAllocator = idAllocator,
			datasource = datasource,
		).initialize()
		advanceUntilIdle()
		return repo
	}

	private fun loadedEvents(projDef: ProjectDef): List<TimeLineEvent> {
		val file = TimeLineDatasource.getTimelineFilePath(projDef).toOkioPath()
		return ffs.readToml<TimeLineContainer>(file, toml).events
	}

	@Test
	fun `reIdEvent rewrites an event id and persists it`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		repo.reIdEvent(oldId = 3, newId = 500)
		advanceUntilIdle()

		val events = loadedEvents(projDef)
		assertNull(events.firstOrNull { it.id == 3 })
		assertEquals("Event 3", events.first { it.id == 500 }.content)
	}

	@Test
	fun `updateEventForSync replaces an existing event in memory`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		val replacement = TimeLineEvent(id = 4, order = 4, date = "synced", content = "From server")
		repo.updateEventForSync(replacement)
		advanceUntilIdle()

		val inMemory = repo.timelineFlow.replayCache.first().events
		assertEquals(replacement, inMemory.first { it.id == 4 })
	}

	@Test
	fun `updateEventForSync appends an unknown event`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		val newEvent = TimeLineEvent(id = 999, order = 99, date = null, content = "Brand new")
		repo.updateEventForSync(newEvent)
		advanceUntilIdle()

		val inMemory = repo.timelineFlow.replayCache.first().events
		assertEquals(fakeEvents().size + 1, inMemory.size)
		assertEquals(newEvent, inMemory.first { it.id == 999 })
	}

	@Test
	fun `storeTimeline persists the in-memory timeline to disk`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		// updateEventForSync mutates memory only; storeTimeline must flush it.
		repo.updateEventForSync(TimeLineEvent(id = 4, order = 4, date = null, content = "Flushed"))
		advanceUntilIdle()
		repo.storeTimeline()

		assertEquals("Flushed", loadedEvents(projDef).first { it.id == 4 }.content)
	}

	@Test
	fun `getTimelineEvent finds a known event and returns null otherwise`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)

		assertEquals("Event 5", repo.getTimelineEvent(5)?.content)
		assertNull(repo.getTimelineEvent(999))
	}

	@Test
	fun `updateEvent marks the entity dirty when the project is server synced`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { syncJournal.markEntityAsDirty(any()) } returns Unit

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		repo.updateEvent(fakeEvents()[2].copy(content = "Edited"))
		advanceUntilIdle()

		coVerify(exactly = 1) { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `updateEvent does not re-mark an already dirty entity`() = runTest {
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns true

		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		setupTimeline(projDef, fakeEvents())

		val repo = initializedRepo(projDef)
		repo.updateEvent(fakeEvents()[2].copy(content = "Edited"))
		advanceUntilIdle()

		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}
}
