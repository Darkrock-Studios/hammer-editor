package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.MissingProjectIdException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
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

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

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
			projectMetadataDatasource = projectMetadataDatasource,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata
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
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

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
			data.entityState,
			ClientEntityState(produceEntityHashSet(1, 2, 3, 4, 5, 6, 7, 10))
		)
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

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata
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

	@Test
	fun `Handle Failure`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { projectMetadataDatasource.loadMetadata(any()) } answers {
			throw Exception("Fail the test")
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertFalse(isSuccess(result))
	}

	@Test
	fun `Missing Server Project ID returns MissingProjectIdException`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		// Create metadata without serverProjectId
		val metadataWithoutServerId = ProjectMetadata(
			info = Info(
				created = Instant.fromEpochSeconds(123456),
				lastAccessed = null,
				dataVersion = PROJECT_DATA_VERSION,
				serverProjectId = null // No server project ID
			)
		)

		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadataWithoutServerId

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		// Should fail with MissingProjectIdException instead of throwing
		assertFalse(isSuccess(result))
		assertIs<MissingProjectIdException>(result.exception)
	}
}