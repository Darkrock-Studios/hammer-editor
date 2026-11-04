package repositories.timeline

import app.cash.turbine.test
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProject1Def
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLineRepositoryMoveTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var json: Json
	lateinit var idRepo: IdAllocator
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var datasource: TimeLineDatasource
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>
	lateinit var syncJournal: SyncJournal
	lateinit var globalSettingsRepo: GlobalSettingsStore
	lateinit var globalSettingsFlow: SharedFlow<GlobalSettings>

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		json = createJsonSerializer()

		idRepo = mockk()
		context = mockk()
		lifecycle = mockk()

		globalSettingsRepo = mockk()
		globalSettingsFlow = mockk()
		every { globalSettingsRepo.globalSettingsUpdates } returns globalSettingsFlow

		syncJournal = mockk()
		every { syncJournal.isServerSynchronized() } returns false

		lifecycleCallbacks = mutableListOf()

		val testModule = module {
			single { idRepo } bind IdAllocator::class
			single { globalSettingsRepo }
			single { syncJournal }
		}
		setupKoin(testModule)

		every { context.lifecycle } returns lifecycle
		every {
			lifecycle.subscribe(capture(lifecycleCallbacks))
		} just Runs

		every { lifecycle.unsubscribe(any()) } just Runs
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
			assertEquals(index, event.order, "Timeline Event Order incorrect")
		}
	}

	private fun TestScope.defaultRepository(
		projectDef: ProjectDef,
		startingEvents: List<TimeLineEvent> = fakeEvents(),
	): TimeLineRepository {
		writeEventsToDisk(projectDef, startingEvents, ffs, toml)

		datasource = TimeLineDatasource(ffs, toml)
		val repo = TimeLineRepository(
			projectDef = projectDef,
			datasource = datasource,
			idAllocator = idRepo,
		)
		repo.initialize()

		advanceUntilIdle()

		return repo
	}

	@Test
	fun `Move event and store to disk`() = runTest {
		val originalEvents = fakeEvents()

		val repository = defaultRepository(getProject1Def(), originalEvents)

		val first = originalEvents.first()
		val index = 4
		val after = false

		val moved = repository.moveEvent(first, index, after)
		assertTrue("Move timeline event failed") { moved }

		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events
		assertEquals(3, newEvents.indexOfFirst { it.id == first.id }, "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 0, 4, 5, 6, 7, 8, 9)

		// The moved order must also be persisted, not just emitted.
		val file = TimeLineDatasource.getTimelineFilePath(getProject1Def()).toOkioPath()
		val onDisk: TimeLineContainer = ffs.readToml(file, toml)
		verifyEventSequence(onDisk.events, 1, 2, 3, 0, 4, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move uses order-sorted positions when disk order differs`() = runTest {
		// Serialized/file order differs from the order field; the UI displays
		// events sorted by order and sends move indices in that sorted space.
		val onDisk = listOf(
			TimeLineEvent(id = 2, order = 2, label = null, content = "Event 2"),
			TimeLineEvent(id = 0, order = 0, label = null, content = "Event 0"),
			TimeLineEvent(id = 1, order = 1, label = null, content = "Event 1"),
		)
		val repository = defaultRepository(getProject1Def(), onDisk)

		// Sorted display is [0, 1, 2]; drag event 0 to the end (after index 2).
		val eventZero = onDisk.first { it.id == 0 }
		val moved = repository.moveEvent(eventZero, 2, true)
		assertTrue("Move timeline event failed") { moved }

		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		verifyEventSequence(newEvents, 1, 2, 0)
	}

	@Test
	fun `Move Fail 0-0`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)


		val first = originalEvents.first()
		val moved = repository.moveEvent(first, 0, false)
		assertFalse("Move should have failed") { moved }
	}

	@Test
	fun `Move event- 0 to 4 before`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val first = originalEvents.first()
		val moved = repository.moveEvent(first, 4, false)
		assertTrue("Move timeline event failed") { moved }

		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		assertEquals(3, newEvents.indexOfFirst { it.id == first.id }, "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 0, 4, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 0 to 4 after`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val first = originalEvents.first()
		val moved = repository.moveEvent(first, 4, true)
		assertTrue("Move timeline event failed") { moved }

		repository.timelineFlow.test {
			val newEvents: List<TimeLineEvent> = awaitItem().events
			assertEquals(
				4,
				newEvents.indexOfFirst { it.id == first.id },
				"Moved item was not at the correct position"
			)
			verifyEventSequence(newEvents, 1, 2, 3, 4, 0, 5, 6, 7, 8, 9)
		}
	}

	@Test
	fun `Move event- 4 to 1 before`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val four = originalEvents[4]
		val moved = repository.moveEvent(four, 1, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events
		assertEquals(1, newEvents.indexOfFirst { it.id == four.id }, "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 0, 4, 1, 2, 3, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 4 to 1 after`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val four = originalEvents[4]
		val moved = repository.moveEvent(four, 1, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events
		assertEquals(2, newEvents.indexOfFirst { it.id == four.id }, "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 0, 1, 4, 2, 3, 5, 6, 7, 8, 9)
	}

	@Test
	fun `Move event- 0 to 10 before`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val zero = originalEvents[0]
		val moved = repository.moveEvent(zero, 9, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		verifyEventSequence(newEvents, 1, 2, 3, 4, 5, 6, 7, 8, 0, 9)
		assertEquals(8, newEvents.indexOfFirst { it.id == zero.id }, "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 0 to 10 after`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val zero = originalEvents[0]
		val moved = repository.moveEvent(zero, 9, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		verifyEventSequence(newEvents, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
		assertEquals(9, newEvents.indexOfFirst { it.id == zero.id }, "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 10 to 0 before`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val ten = originalEvents[9]
		val moved = repository.moveEvent(ten, 0, false)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		verifyEventSequence(newEvents, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8)
		assertEquals(0, newEvents.indexOfFirst { it.id == ten.id }, "Moved item was not at the correct position")
	}

	@Test
	fun `Move event- 10 to 0 after`() = runTest {
		val originalEvents = fakeEvents()
		val repository = defaultRepository(getProject1Def(), originalEvents)

		val ten = originalEvents[9]
		val moved = repository.moveEvent(ten, 0, true)
		assertTrue("Move timeline event failed") { moved }
		advanceUntilIdle()

		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events

		verifyEventSequence(newEvents, 0, 9, 1, 2, 3, 4, 5, 6, 7, 8)
		assertEquals(1, newEvents.indexOfFirst { it.id == ten.id }, "Moved item was not at the correct position")
	}

	private fun interleavedEvents(): List<TimeLineEvent> {
		val main = (0..4).map { TimeLineEvent(id = it, order = it, content = "Main $it") }
		val other = (0..3).map { TimeLineEvent(id = 10 + it, order = it, content = "Other $it", timeline = OTHER) }
		return listOf(main[0], other[0], main[1], other[1], main[2], other[2], main[3], other[3], main[4])
	}

	private fun dirtyIdsCollector(): MutableList<Int> {
		val dirty = mutableListOf<Int>()
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false
		coEvery { syncJournal.markEntityAsDirty(capture(dirty)) } returns Unit
		return dirty
	}

	private fun List<TimeLineEvent>.on(timeline: String?) = filter { it.timeline == timeline }.sortedBy { it.order }

	@Test
	fun `Moving on one timeline renumbers and dirties only that timeline`() = runTest {
		val dirty = dirtyIdsCollector()
		val repository = defaultRepository(getProject1Def(), interleavedEvents())

		// Other in order is [10, 11, 12, 13]; move 10 after index 2.
		val moved = repository.moveEvent(interleavedEvents()[1], 2, true)
		assertTrue(moved)

		advanceUntilIdle()
		val newEvents = repository.timelineFlow.first().events
		verifyEventSequence(newEvents.on(OTHER), 11, 12, 10, 13)
		assertEquals(interleavedEvents().on(null), newEvents.on(null), "main is untouched")
		assertEquals(setOf(11, 12, 10), dirty.toSet())
		assertEquals(3, dirty.size)

		val file = TimeLineDatasource.getTimelineFilePath(getProject1Def()).toOkioPath()
		val onDisk: TimeLineContainer = ffs.readToml(file, toml)
		verifyEventSequence(onDisk.events.on(OTHER), 11, 12, 10, 13)
		assertEquals(interleavedEvents().on(null), onDisk.events.on(null))
	}

	@Test
	fun `Moving on main uses positions among main's events only`() = runTest {
		val dirty = dirtyIdsCollector()
		val repository = defaultRepository(getProject1Def(), interleavedEvents())

		// Main in order is [0, 1, 2, 3, 4]; index 4 is main's last, not the file's fifth event.
		val moved = repository.moveEvent(interleavedEvents()[0], 4, false)
		assertTrue(moved)

		advanceUntilIdle()
		val newEvents = repository.timelineFlow.first().events
		verifyEventSequence(newEvents.on(null), 1, 2, 3, 0, 4)
		assertEquals(interleavedEvents().on(OTHER), newEvents.on(OTHER), "the other timeline is untouched")
		assertEquals(setOf(0, 1, 2, 3), dirty.toSet())
	}

	@Test
	fun `A move past the end of the event's own timeline fails`() = runTest {
		val repository = defaultRepository(getProject1Def(), interleavedEvents())

		// In range for the whole file, but the other timeline has only four events.
		assertFalse(repository.moveEvent(interleavedEvents()[1], 4, false))
		assertEquals(interleavedEvents(), repository.timelineFlow.first().events)
	}

	private companion object {
		const val OTHER = "a3f09c1e"
	}
}
