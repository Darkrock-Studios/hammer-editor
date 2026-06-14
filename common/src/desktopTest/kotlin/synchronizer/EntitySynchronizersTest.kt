package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EntitySynchronizersTest : BaseTest() {

	private val projectDef: ProjectDef = getProjectDef("Test Project 2")
	private lateinit var mocks: MockSynchronizers

	@BeforeEach
	override fun setup() {
		super.setup()
		mocks = MockSynchronizers(true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				addSynchronizers(mocks)
			}
		})
	}

	private fun entitySynchronizers() = EntitySynchronizers(projectDef)

	@Test
	fun `get returns the synchronizer registered for each type`() {
		val es = entitySynchronizers()

		assertSame(mocks.sceneSynchronizer, es[EntityType.Scene])
		assertSame(mocks.noteSynchronizer, es[EntityType.Note])
		assertSame(mocks.timelineSynchronizer, es[EntityType.TimelineEvent])
		assertSame(mocks.encyclopediaSynchronizer, es[EntityType.EncyclopediaEntry])
		assertSame(mocks.sceneDraftSynchronizer, es[EntityType.SceneDraft])
	}

	@Test
	fun `findEntityType reports the owning type and null when unowned`() = runTest {
		coEvery { mocks.noteSynchronizer.ownsEntity(7) } returns true

		val es = entitySynchronizers()

		assertEquals(EntityType.Note, es.findEntityType(7))
		assertNull(es.findEntityType(999))
	}

	@Test
	fun `clientHasEntity reflects ownership`() = runTest {
		coEvery { mocks.timelineSynchronizer.ownsEntity(3) } returns true

		val es = entitySynchronizers()

		assertTrue(es.clientHasEntity(3))
		assertTrue(!es.clientHasEntity(4))
	}

	@Test
	fun `findById returns the owning synchronizer and null when unowned`() = runTest {
		coEvery { mocks.encyclopediaSynchronizer.ownsEntity(11) } returns true

		val es = entitySynchronizers()

		assertSame(mocks.encyclopediaSynchronizer, es.findById(11))
		assertNull(es.findById(12))
	}

	@Test
	fun `getLocalEntityHash routes to the owner for every type and is null when unowned`() = runTest {
		val cases = listOf(
			Triple(mocks.sceneSynchronizer, 1, "scene-hash"),
			Triple(mocks.noteSynchronizer, 2, "note-hash"),
			Triple(mocks.timelineSynchronizer, 3, "timeline-hash"),
			Triple(mocks.encyclopediaSynchronizer, 4, "encyclopedia-hash"),
			Triple(mocks.sceneDraftSynchronizer, 5, "draft-hash"),
		)
		cases.forEach { (sync, id, hash) ->
			coEvery { sync.ownsEntity(id) } returns true
			coEvery { sync.getEntityHash(id) } returns hash
		}

		val es = entitySynchronizers()

		cases.forEach { (_, id, hash) -> assertEquals(hash, es.getLocalEntityHash(id)) }
		assertNull(es.getLocalEntityHash(404))
	}

	@Test
	fun `deleteEntityLocal routes to the owning synchronizer`() = runTest {
		coEvery { mocks.noteSynchronizer.ownsEntity(8) } returns true

		val es = entitySynchronizers()
		es.deleteEntityLocal(8, onLog = {})

		coVerify(exactly = 1) { mocks.noteSynchronizer.deleteEntityLocal(8, any()) }
	}

	@Test
	fun `deleteEntityLocal is a no-op for an unowned id`() = runTest {
		val es = entitySynchronizers()
		es.deleteEntityLocal(404, onLog = {})

		coVerify(exactly = 0) { mocks.sceneSynchronizer.deleteEntityLocal(any(), any()) }
		coVerify(exactly = 0) { mocks.noteSynchronizer.deleteEntityLocal(any(), any()) }
	}

	@Test
	fun `reIdEntry routes to the owning synchronizer for every type`() = runTest {
		val cases = listOf(
			mocks.sceneSynchronizer to 1,
			mocks.noteSynchronizer to 2,
			mocks.timelineSynchronizer to 3,
			mocks.encyclopediaSynchronizer to 4,
			mocks.sceneDraftSynchronizer to 5,
		)
		cases.forEach { (sync, id) -> coEvery { sync.ownsEntity(id) } returns true }

		val es = entitySynchronizers()
		cases.forEach { (_, id) -> es.reIdEntry(oldId = id, newId = id + 100) }

		cases.forEach { (sync, id) ->
			coVerify(exactly = 1) { sync.reIdEntity(oldId = id, newId = id + 100) }
		}
	}

	@Test
	fun `reIdEntry skips a phantom id that no synchronizer owns`() = runTest {
		val es = entitySynchronizers()
		es.reIdEntry(oldId = 404, newId = 9)

		coVerify(exactly = 0) { mocks.sceneSynchronizer.reIdEntity(any(), any()) }
		coVerify(exactly = 0) { mocks.noteSynchronizer.reIdEntity(any(), any()) }
		coVerify(exactly = 0) { mocks.timelineSynchronizer.reIdEntity(any(), any()) }
		coVerify(exactly = 0) { mocks.encyclopediaSynchronizer.reIdEntity(any(), any()) }
		coVerify(exactly = 0) { mocks.sceneDraftSynchronizer.reIdEntity(any(), any()) }
	}

	@Test
	fun `handleConflict routes each entity to its synchronizer's resolution channel`() = runTest {
		val sceneCh = Channel<ApiProjectEntity.SceneEntity>(Channel.UNLIMITED)
		val noteCh = Channel<ApiProjectEntity.NoteEntity>(Channel.UNLIMITED)
		val timelineCh = Channel<ApiProjectEntity.TimelineEventEntity>(Channel.UNLIMITED)
		val encyclopediaCh = Channel<ApiProjectEntity.EncyclopediaEntryEntity>(Channel.UNLIMITED)
		val draftCh = Channel<ApiProjectEntity.SceneDraftEntity>(Channel.UNLIMITED)

		every { mocks.sceneSynchronizer.conflictResolution } returns sceneCh
		every { mocks.noteSynchronizer.conflictResolution } returns noteCh
		every { mocks.timelineSynchronizer.conflictResolution } returns timelineCh
		every { mocks.encyclopediaSynchronizer.conflictResolution } returns encyclopediaCh
		every { mocks.sceneDraftSynchronizer.conflictResolution } returns draftCh

		val scene = mockk<ApiProjectEntity.SceneEntity>()
		val note = mockk<ApiProjectEntity.NoteEntity>()
		val timeline = mockk<ApiProjectEntity.TimelineEventEntity>()
		val encyclopedia = mockk<ApiProjectEntity.EncyclopediaEntryEntity>()
		val draft = mockk<ApiProjectEntity.SceneDraftEntity>()

		val es = entitySynchronizers()
		es.handleConflict(scene)
		es.handleConflict(note)
		es.handleConflict(timeline)
		es.handleConflict(encyclopedia)
		es.handleConflict(draft)

		assertSame(scene, sceneCh.tryReceive().getOrNull())
		assertSame(note, noteCh.tryReceive().getOrNull())
		assertSame(timeline, timelineCh.tryReceive().getOrNull())
		assertSame(encyclopedia, encyclopediaCh.tryReceive().getOrNull())
		assertSame(draft, draftCh.tryReceive().getOrNull())
	}
}
