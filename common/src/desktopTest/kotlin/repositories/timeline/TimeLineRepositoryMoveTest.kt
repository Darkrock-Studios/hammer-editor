package repositories.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import getProject1Def
import io.mockk.Runs
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
	lateinit var idRepo: IdRepository
	lateinit var context: ComponentContext
	lateinit var lifecycle: Lifecycle
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>
	lateinit var projectSynchronizer: ClientProjectSynchronizer
	lateinit var globalSettingsRepo: GlobalSettingsRepository
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

		projectSynchronizer = mockk()
		every { projectSynchronizer.isServerSynchronized() } returns false

		lifecycleCallbacks = mutableListOf()

		val testModule = module {
			single { idRepo } bind IdRepository::class
			single { globalSettingsRepo }
			single { projectSynchronizer }
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

		val repo = TimeLineRepositoryOkio(
			projectDef = projectDef,
			fileSystem = ffs,
			toml = toml,
			idRepository = idRepo,
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
		advanceUntilIdle()
		val newEvents: List<TimeLineEvent> = repository.timelineFlow.first().events
		assertEquals(4, newEvents.indexOfFirst { it.id == first.id }, "Moved item was not at the correct position")

		verifyEventSequence(newEvents, 1, 2, 3, 4, 0, 5, 6, 7, 8, 9)
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
}