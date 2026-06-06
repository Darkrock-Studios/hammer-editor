package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestStrRes
import kotlin.test.*

class ClientTimelineSynchronizerTest {

	private val projectDef = ProjectDef(name = "Test", path = HPath("/projects/Test", "Test", false))
	private val strRes: StrRes = TestStrRes()

	private val serverProjectApi: ServerProjectApi = mockk()
	private val projectMetadataDatasource: ProjectMetadataDatasource = mockk()
	private val timeLineRepository: TimeLineRepository = mockk(relaxed = true)

	private fun event(id: Int, order: Int = 0, content: String = "stuff") =
		TimeLineEvent(id = id, order = order, date = "1920", content = content, tags = setOf("a"))

	private fun newSynchronizer() = ClientTimelineSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
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
	fun `getEntityHash returns a hash when present and null when absent`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5)
		coEvery { timeLineRepository.getTimelineEvent(99) } returns null
		val sync = newSynchronizer()
		assertNotNull(sync.getEntityHash(5))
		assertNull(sync.getEntityHash(99))
	}

	@Test
	fun `createEntityForId maps the event fields`() = runTest {
		coEvery { timeLineRepository.getTimelineEvent(5) } returns event(5, order = 3, content = "battle")

		val entity = newSynchronizer().createEntityForId(5)

		assertEquals(5, entity.id)
		assertEquals(3, entity.order)
		assertEquals("battle", entity.content)
		assertEquals("1920", entity.date)
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
			date = "1850",
			content = "server content",
			tags = setOf("srv"),
		)

		val result = newSynchronizer().storeEntity(serverEntity, "sync-1", {})

		assertTrue(result)
		coVerify {
			timeLineRepository.updateEventForSync(
				TimeLineEvent(id = 5, order = 2, date = "1850", content = "server content", tags = setOf("srv"))
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
}
