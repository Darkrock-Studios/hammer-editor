package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.SyncOperationException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class FetchLocalDataOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var syncJournal: SyncJournal

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

	private fun createOperation(projectDef: ProjectDef): FetchLocalDataOperation {
		configureKoin(projectDef)
		return FetchLocalDataOperation(
			projectDef = projectDef,
			entitySynchronizers = EntitySynchronizers(projectDef),
			strRes = strRes,
			syncJournal = syncJournal,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { syncJournal.loadSyncData() } returns projectData

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			when (synchronizer) {
				is ClientSceneSynchronizer ->
					coEvery { synchronizer.hashEntities(any()) } returns produceEntityHashSet(
						1,
						2,
						3,
						4
					)

				is ClientNoteSynchronizer ->
					coEvery { synchronizer.hashEntities(any()) } returns produceEntityHashSet(
						5,
						6,
						7,
						10
					)

				else -> coEvery { synchronizer.hashEntities(any()) } returns emptySet()
			}
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<FetchLocalDataState>(data)

		// Backfill gives every entity a baseline: dirty entities (1, 3) recover the frozen hash from
		// the dirty list, in-sync entities (2, 4, 5, 6, 7, 10) take their local hash. The new id
		// (11) is absent from the entity state, so it stays unbackfilled.
		val expectedSyncData = projectData.copy(
			syncedHashes = mapOf(
				1 to "old-hash-1",
				3 to "old-hash-3",
				2 to "hash-2",
				4 to "hash-4",
				5 to "hash-5",
				6 to "hash-6",
				7 to "hash-7",
				10 to "hash-10",
			)
		)
		assertEquals(expectedSyncData, data.clientSyncData)
		assertEquals(
			ClientEntityState(produceEntityHashSet(1, 2, 3, 4, 5, 6, 7, 10)),
			data.entityState
		)
		// The backfilled baselines must be persisted, not just held in memory.
		coVerify { syncJournal.saveSyncData(expectedSyncData) }
	}

	@Test
	fun `Backfill leaves already-recorded baselines untouched`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		val existing = ProjectSynchronizationData(
			lastId = 3,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = produceEntityStateList(1),
			deletedIds = emptySet(),
			syncedHashes = mapOf(2 to "already-recorded-2"),
		)

		coEvery { syncJournal.loadSyncData() } returns existing

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			when (synchronizer) {
				is ClientSceneSynchronizer ->
					coEvery { synchronizer.hashEntities(any()) } returns produceEntityHashSet(1, 2, 3)

				else -> coEvery { synchronizer.hashEntities(any()) } returns emptySet()
			}
		}

		val result = op.execute(
			state = InitialSyncOperationState(false),
			onProgress = mockk(relaxed = true),
			onLog = mockk(relaxed = true),
			onConflict = mockk(relaxed = true),
			onComplete = mockk(relaxed = true),
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<FetchLocalDataState>(data)
		assertEquals(
			mapOf(
				2 to "already-recorded-2", // untouched — never overwritten
				1 to "old-hash-1",         // dirty: recovered from the frozen baseline
				3 to "hash-3",             // in-sync: local hash
			),
			data.clientSyncData.syncedHashes,
		)
	}

	private fun stubSceneHashes(vararg ids: Int) {
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			when (synchronizer) {
				is ClientSceneSynchronizer ->
					coEvery { synchronizer.hashEntities(any()) } returns produceEntityHashSet(*ids)

				else -> coEvery { synchronizer.hashEntities(any()) } returns emptySet()
			}
		}
	}

	@Test
	fun `A restored project marks every entity dirty against its best known baseline`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		val restored = ProjectSynchronizationData(
			lastId = 5,
			newIds = listOf(5),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = listOf(EntityOriginalState(1, "old-hash-1"), EntityOriginalState(4, null)),
			deletedIds = emptySet(),
			syncedHashes = mapOf(1 to "confirmed-1", 2 to "confirmed-2"),
			verifyAllEntities = true,
		)
		coEvery { syncJournal.loadSyncData() } returns restored
		stubSceneHashes(1, 2, 3, 4)

		val result = op.execute(
			state = InitialSyncOperationState(false),
			onProgress = mockk(relaxed = true),
			onLog = mockk(relaxed = true),
			onConflict = mockk(relaxed = true),
			onComplete = mockk(relaxed = true),
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<FetchLocalDataState>(data)
		// Dirty means uploaded rather than replaced by the server's copy; the baseline means the
		// server conflict-checks that upload. The new id (5) uploads as new regardless. The flag
		// stays set until the sync completes, so an id adopted mid-sync keeps these baselines.
		val expected = restored.copy(
			dirty = listOf(
				EntityOriginalState(1, "old-hash-1"),
				EntityOriginalState(4, null),
				EntityOriginalState(2, "confirmed-2"),
				EntityOriginalState(3, "hash-3"),
			),
			syncedHashes = mapOf(
				1 to "confirmed-1",
				2 to "confirmed-2",
				3 to "hash-3",
				4 to "hash-4",
			),
		)
		assertEquals(expected, data.clientSyncData)
		coVerify { syncJournal.saveSyncData(expected) }
	}

	@Test
	fun `A lite sync leaves a restored project's check for the next full sync`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		val restored = ProjectSynchronizationData(
			lastId = 3,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(123456),
			dirty = emptyList(),
			deletedIds = emptySet(),
			syncedHashes = mapOf(1 to "confirmed-1"),
			verifyAllEntities = true,
		)
		coEvery { syncJournal.loadSyncData() } returns restored
		stubSceneHashes(1, 2, 3)

		val result = op.execute(
			state = InitialSyncOperationState(true),
			onProgress = mockk(relaxed = true),
			onLog = mockk(relaxed = true),
			onConflict = mockk(relaxed = true),
			onComplete = mockk(relaxed = true),
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<FetchLocalDataState>(data)
		assertEquals(restored, data.clientSyncData)
		coVerify(exactly = 0) { syncJournal.saveSyncData(any()) }
	}

	@Test
	fun `Handle Failure`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { syncJournal.loadSyncData() } answers {
			throw Exception("Fail the test")
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertFalse(isSuccess(result))
		assertIs<SyncOperationException>(result.exception)
	}

}
