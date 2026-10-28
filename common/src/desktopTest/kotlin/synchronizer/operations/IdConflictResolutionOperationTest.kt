package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class IdConflictResolutionOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var idAllocator: IdAllocator

	private lateinit var strRes: TestStrRes

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		strRes = TestStrRes()
		mockSynchronizers = MockSynchronizers(false)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)
			}
		})
	}

	private fun createOperation(projectDef: ProjectDef): IdConflictResolutionOperation {
		configureKoin(projectDef)
		return IdConflictResolutionOperation(
			projectDef = projectDef,
			idAllocator = idAllocator,
			entitySynchronizers = EntitySynchronizers(projectDef),
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)
		val told = mutableListOf<Pair<ProjectDef, List<EntityIdChange>>>()
		loadKoinModules(module { single<EntityIdChangeListener> { EntityIdChangeListener { project, changes -> told += project to changes } } })
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 11

		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(11) } returns true
		coEvery { mockSynchronizers.sceneSynchronizer.deleteEntityLocal(any(), any()) } just Runs
		coEvery { mockSynchronizers.sceneSynchronizer.reIdEntity(any(), any()) } returns emptySet()

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = CollateIdsState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverSyncData = beganResponse,
			collatedIds = collatedIds,
		)

		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<IdConflictResolutionState>(data)

		assertEquals(12, data.maxId)
		assertEquals(listOf(12), data.newClientIds)
		// The remap must also land in the client sync data that later operations persist.
		assertEquals(listOf(12), data.clientSyncData.newIds)
		assertEquals(12, data.clientSyncData.lastId)
		assertEquals(
			produceEntityStateList(1, 3) + EntityOriginalState(12, "old-hash-11"),
			data.clientSyncData.dirty
		)

		coVerify { mockSynchronizers.sceneSynchronizer.deleteEntityLocal(11, any()) }
		coVerify { mockSynchronizers.sceneSynchronizer.reIdEntity(11, 12) }
		assertEquals(listOf(projectDef to listOf(EntityIdChange(EntityType.Scene, 11, 12))), told)
	}

	@Test
	fun `Phantom newId owned by no synchronizer is skipped, not fatal`() = runTest {
		// Reproduces the production "Entity X not found for reId" wedge: a newId
		// that no local entity backs (created then deleted before a successful
		// sync). Once the server's lastId climbs past it, the re-ID branch fires
		// and used to throw, aborting every sync forever. It must instead skip
		// the phantom so the sync can finish and clear it.
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 50

		// No synchronizer owns the phantom id.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(any()) } returns false
		coEvery { mockSynchronizers.noteSynchronizer.ownsEntity(any()) } returns false
		coEvery { mockSynchronizers.timelineSynchronizer.ownsEntity(any()) } returns false
		coEvery { mockSynchronizers.encyclopediaSynchronizer.ownsEntity(any()) } returns false
		coEvery { mockSynchronizers.sceneDraftSynchronizer.ownsEntity(any()) } returns false
		coEvery { mockSynchronizers.imageSynchronizer.ownsEntity(any()) } returns false

		val phantomClientData = ProjectSynchronizationData(
			lastId = 50,
			newIds = listOf(50),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = emptyList(),
			deletedIds = emptySet(),
		)
		// Server lastId is past the phantom, so the re-ID branch activates.
		val serverBegan = ProjectSynchronizationBegan(
			syncId = "sync-id",
			lastSync = Instant.fromEpochSeconds(1234567),
			lastId = 60,
			idSequence = emptyList(),
			deletedIds = emptySet(),
		)
		val emptyCollatedIds = CollateIdsState.CollatedIds(
			combinedDeletions = emptySet(),
			serverDeletedIds = emptySet(),
			newlyDeletedIds = emptySet(),
			dirtyEntities = mutableListOf(),
		)

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = CollateIdsState(
			onlyNew = false,
			clientSyncData = phantomClientData,
			entityState = entityState,
			serverSyncData = serverBegan,
			collatedIds = emptyCollatedIds,
		)

		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		coVerify(exactly = 0) { mockSynchronizers.sceneSynchronizer.reIdEntity(any(), any()) }
		coVerify(exactly = 0) { mockSynchronizers.encyclopediaSynchronizer.reIdEntity(any(), any()) }
	}

	@Test
	fun `New IDs are assigned above local max when server lastId regressed`() = runTest {
		// Guards against server-side data loss: if the server reports a lastId
		// lower than an existing local entity's ID, new IDs must still be assigned
		// above the local max. Seeding only from serverLastId would hand out IDs
		// that collide with - and clobber - real local entities.
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 100 // local max entity id

		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(30) } returns true
		coEvery { mockSynchronizers.sceneSynchronizer.reIdEntity(any(), any()) } returns emptySet()

		val clientData = ProjectSynchronizationData(
			lastId = 100,
			newIds = listOf(30),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = emptyList(),
			deletedIds = emptySet(),
		)
		// Server reports a lastId below the local max - the regression we guard against.
		val serverBegan = ProjectSynchronizationBegan(
			syncId = "sync-id",
			lastSync = Instant.fromEpochSeconds(1234567),
			lastId = 50,
			idSequence = emptyList(),
			deletedIds = emptySet(),
		)
		val emptyCollatedIds = CollateIdsState.CollatedIds(
			combinedDeletions = emptySet(),
			serverDeletedIds = emptySet(),
			newlyDeletedIds = emptySet(),
			dirtyEntities = mutableListOf(),
		)

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = CollateIdsState(
			onlyNew = false,
			clientSyncData = clientData,
			entityState = entityState,
			serverSyncData = serverBegan,
			collatedIds = emptyCollatedIds,
		)

		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		// Must land above the local max (101), not 51 which would clobber a real entity.
		coVerify { mockSynchronizers.sceneSynchronizer.reIdEntity(30, 101) }
	}

	private fun stubNobodyOwns() {
		mockSynchronizers.synchronizers.forEach { sync ->
			coEvery { sync.ownsEntity(any()) } returns false
		}
	}

	private suspend fun runResolution(
		op: IdConflictResolutionOperation,
		clientData: ProjectSynchronizationData,
		serverLastId: Int,
	): IdConflictResolutionState {
		val state = CollateIdsState(
			onlyNew = false,
			clientSyncData = clientData,
			entityState = entityState,
			serverSyncData = ProjectSynchronizationBegan(
				syncId = "sync-id",
				lastSync = Instant.fromEpochSeconds(1234567),
				lastId = serverLastId,
				idSequence = emptyList(),
				deletedIds = emptySet(),
			),
			collatedIds = CollateIdsState.CollatedIds(
				combinedDeletions = emptySet(),
				serverDeletedIds = emptySet(),
				newlyDeletedIds = emptySet(),
				dirtyEntities = mutableListOf(),
			),
		)
		val result = op.execute(state, { _, _ -> }, {}, {}, {})
		assertTrue(isSuccess(result))
		return assertIs<IdConflictResolutionState>(result.data)
	}

	@Test
	fun `Entities a re-ID rewrote become dirty with their synced baseline and are recorded`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 12

		stubNobodyOwns()
		coEvery { mockSynchronizers.encyclopediaSynchronizer.ownsEntity(11) } returns true
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(12) } returns true
		// Re-IDing entry 11 rewrites scene 5 (synced) and scene 12 (new, re-IDed after it).
		coEvery { mockSynchronizers.encyclopediaSynchronizer.reIdEntity(11, 13) } returns setOf(5, 12)
		coEvery { mockSynchronizers.sceneSynchronizer.reIdEntity(12, 14) } returns emptySet()

		val clientData = ProjectSynchronizationData(
			lastId = 12,
			newIds = listOf(11, 12),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = listOf(EntityOriginalState(3, "synced-3")),
			deletedIds = emptySet(),
			syncedHashes = mapOf(3 to "synced-3", 5 to "synced-5"),
		)

		val data = runResolution(op, clientData, serverLastId = 12)

		assertEquals(listOf(13, 14), data.newClientIds)
		// Scene 12 was itself re-IDed later in the loop, so it is recorded under its final id.
		assertEquals(setOf(5, 14), data.collatedIds.rewrittenIds)
		assertEquals(
			listOf(
				EntityOriginalState(3, "synced-3"),
				EntityOriginalState(5, "synced-5"),
				EntityOriginalState(14, null),
			),
			data.clientSyncData.dirty,
		)
	}

	@Test
	fun `A rewritten entity that is already dirty keeps its one dirty record`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 11

		stubNobodyOwns()
		coEvery { mockSynchronizers.encyclopediaSynchronizer.ownsEntity(11) } returns true
		coEvery { mockSynchronizers.encyclopediaSynchronizer.reIdEntity(11, 12) } returns setOf(5)

		val clientData = ProjectSynchronizationData(
			lastId = 11,
			newIds = listOf(11),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = listOf(EntityOriginalState(5, "older-baseline")),
			deletedIds = emptySet(),
			syncedHashes = mapOf(5 to "synced-5"),
		)

		val data = runResolution(op, clientData, serverLastId = 11)

		assertEquals(setOf(5), data.collatedIds.rewrittenIds)
		assertEquals(listOf(EntityOriginalState(5, "older-baseline")), data.clientSyncData.dirty)
	}

	@Test
	fun `No re-ID means nothing is recorded as rewritten`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idAllocator.findNextId() } just Runs
		coEvery { idAllocator.peekLastId() } returns 11

		val clientData = ProjectSynchronizationData(
			lastId = 11,
			newIds = listOf(11),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = emptyList(),
			deletedIds = emptySet(),
		)

		val data = runResolution(op, clientData, serverLastId = 10)

		assertTrue(data.collatedIds.rewrittenIds.isEmpty())
	}
}
