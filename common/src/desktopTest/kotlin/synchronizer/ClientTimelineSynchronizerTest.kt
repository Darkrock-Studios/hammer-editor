package synchronizer

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import createProject
import getProjectDef
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.timeline.writeEventsToDisk
import testProjectId
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.*

class ClientTimelineSynchronizerTest : BaseTest() {

	private val projectDef = ProjectDef(id = testProjectId("Test"), name = "Test", path = HPath("/projects/Test", "Test", false))
	private val strRes: StrRes = TestStrRes()

	private val serverProjectApi: ServerProjectApi = mockk()
	private val timeLineRepository: TimeLineRepository = mockk(relaxed = true)

	private fun event(id: Int, order: Int = 0, content: String = "stuff") =
		TimeLineEvent(id = id, order = order, label = "1920", content = content, tags = setOf("a"))

	private fun newSynchronizer() = ClientTimelineSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		timeLineRepository = timeLineRepository,
		strRes = strRes,
	)

	@Test
	fun `getEntityType is TimelineEvent`() {
		assertEquals(EntityType.TimelineEvent, newSynchronizer().getEntityType())
	}

	@Test
	fun `ownsEntity reflects whether the event exists`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5)
		coEvery { timeLineRepository.getTimelineEvent(99) } returns null
		val sync = newSynchronizer()
		assertTrue(sync.ownsEntity(5))
		assertFalse(sync.ownsEntity(99))
	}

	@Test
	fun `getEntityHash is deterministic and content-sensitive and null when absent`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5)
		coEvery { timeLineRepository.getTimelineEvent(99) } returns null
		val sync = newSynchronizer()

		val hash = sync.getEntityHash(5)
		assertNotNull(hash)
		assertEquals(hash, sync.getEntityHash(5))

		// Event content must feed the sync hash, or edits never propagate.
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5, content = "changed")
		assertNotEquals(hash, sync.getEntityHash(5))

		assertNull(sync.getEntityHash(99))
	}

	@Test
	fun `createEntityForId maps the event fields`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5, order = 3, content = "battle")

		val entity = newSynchronizer().createEntityForId(5)

		assertEquals(5, entity.id)
		assertEquals(3, entity.order)
		assertEquals("battle", entity.content)
		assertEquals("1920", entity.label)
		assertEquals(setOf("a"), entity.tags)
	}

	@Test
	fun `createEntityForId throws when the event is missing`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(99) } returns null
		assertFailsWith<IllegalStateException> { newSynchronizer().createEntityForId(99) }
	}

	@Test
	fun `storeEntity updates the event for sync`() = runTest {
		val serverEntity = ApiProjectEntity.TimelineEventEntity(
			id = 5,
			order = 2,
			label = "1850",
			content = "server content",
			tags = setOf("srv"),
		)

		val result = newSynchronizer().storeEntity(serverEntity, "sync-1", {})

		assertTrue(result)
		coVerify {
			timeLineRepository.updateEventForSync(
				TimeLineEvent(id = 5, order = 2, label = "1850", content = "server content", tags = setOf("srv"))
			)
		}
	}

	@Test
	fun `reIdEntity delegates to the repository`() = runTest {
		newSynchronizer().reIdEntity(oldId = 3, newId = 9)
		coVerify { timeLineRepository.reIdEvent(3, 9) }
	}

	@Test
	fun `deleteEntityLocal logs info when the event is deleted`() = runTest {
		val target = event(7)
		coEvery { timeLineRepository.getTimelineEvent(7) } returns target
		coEvery { timeLineRepository.deleteEvent(target) } returns true

		val logs = mutableListOf<SyncLogMessage>()
		newSynchronizer().deleteEntityLocal(7) { logs.add(it) }

		coVerify { timeLineRepository.deleteEvent(target) }
		assertEquals(SyncLogLevel.INFO, logs.single().level)
	}

	@Test
	fun `deleteEntityLocal logs error when the delete fails`() = runTest {
		val target = event(7)
		coEvery { timeLineRepository.getTimelineEvent(7) } returns target
		coEvery { timeLineRepository.deleteEvent(target) } returns false

		val logs = mutableListOf<SyncLogMessage>()
		newSynchronizer().deleteEntityLocal(7) { logs.add(it) }

		assertEquals(SyncLogLevel.ERROR, logs.single().level)
	}

	@Test
	fun `deleteEntityLocal logs error when the event is not found`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(7) } returns null

		val logs = mutableListOf<SyncLogMessage>()
		newSynchronizer().deleteEntityLocal(7) { logs.add(it) }

		assertEquals(SyncLogLevel.ERROR, logs.single().level)
		coVerify(exactly = 0) { timeLineRepository.deleteEvent(any()) }
	}

	@Test
	fun `Local hash equals the API hash after a store, for every entry shape`() = runTest {
		val ffs = FakeFileSystem()
		val toml = createTomlSerializer()
		setupKoin(module { single<SyncJournal> { mockk(relaxed = true) } })
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projDef = getProjectDef(PROJECT_EMPTY_NAME)
		writeEventsToDisk(projDef, emptyList(), ffs, toml)

		val serverEntities = listOf(
			ApiProjectEntity.TimelineEventEntity(
				id = 1,
				order = 0,
				label = null,
				content = "Legacy event",
				tags = setOf("old"),
			),
			ApiProjectEntity.TimelineEventEntity(
				id = 2,
				order = 1,
				label = "  Late autumn  ",
				content = "Wind takes the roof.\n\nSecond paragraph.",
				tags = setOf("weather", "storm"),
				references = setOf(12, 31),
				lane = "The  Keep",
				milestone = true,
				start = StoryDate(mapOf("year" to 97L, "day" to 210L)),
				title = " The storm ",
			),
			ApiProjectEntity.TimelineEventEntity(
				id = 3,
				order = 2,
				label = null,
				content = "",
				kind = TimelineKind.SCENE,
				subject = 31,
				lane = "Alice",
				start = StoryDate(mapOf("year" to -2L, "day" to 214L, "hour" to 6L, "minute" to 30L)),
				cachedTitle = "Landfall",
			),
			ApiProjectEntity.TimelineEventEntity(
				id = 4,
				order = 3,
				label = null,
				content = "",
				kind = TimelineKind.ENTRY,
				subject = 34,
				start = StoryDate(mapOf("year" to 97L)),
				end = StoryDate(mapOf("year" to 98L, "day" to 3L)),
			),
		)

		val repo = TimeLineRepository(projDef, mockk<IdAllocator>(), TimeLineDatasource(ffs, toml)).initialize()
		advanceUntilIdle()
		val sync = ClientTimelineSynchronizer(projDef, serverProjectApi, repo, strRes)

		serverEntities.forEach { sync.storeEntity(it, "sync-1", {}) }
		sync.finalizeSync()

		serverEntities.forEach { serverEntity ->
			val id = serverEntity.id
			assertEquals(serverEntity, sync.createEntityForId(id), "entity $id")
			assertEquals(sync.createEntityForId(id).hash(), sync.getEntityHash(id), "entity $id")
			assertEquals(serverEntity.hash(), sync.getEntityHash(id), "entity $id")
		}

		// Through the file too: a fresh repository reads back what was stored.
		val reloaded = TimeLineRepository(projDef, mockk<IdAllocator>(), TimeLineDatasource(ffs, toml)).initialize()
		advanceUntilIdle()
		val reloadedSync = ClientTimelineSynchronizer(projDef, serverProjectApi, reloaded, strRes)
		serverEntities.forEach { serverEntity ->
			assertEquals(serverEntity.hash(), reloadedSync.getEntityHash(serverEntity.id), "reloaded ${serverEntity.id}")
		}
	}
}
