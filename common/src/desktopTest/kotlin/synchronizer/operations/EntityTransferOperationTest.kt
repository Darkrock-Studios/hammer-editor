package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.DeleteIdsResponse
import com.darkrockstudios.apps.hammer.base.http.LoadEntityResponse
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.EntityNotFoundException
import com.darkrockstudios.apps.hammer.common.server.EntityNotModifiedException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.StaleServerHashException
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import utils.TestClock
import utils.TestStrRes
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class EntityTransferOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = false)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var syncJournal: SyncJournal

	private lateinit var strRes: TestStrRes

	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		strRes = TestStrRes()
		clock = TestClock(Clock.System)
		mockSynchronizers = MockSynchronizers(true)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)
			}
		})
	}

	private fun createOperation(projectDef: ProjectDef): EntityTransferOperation {
		configureKoin(projectDef)
		return EntityTransferOperation(
			projectDef = projectDef,
			serverProjectApi = serverProjectApi,
			strRes = strRes,
			entitySynchronizers = EntitySynchronizers(projectDef),
			projectMetadataDatasource = projectMetadataDatasource,
			syncJournal = syncJournal,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		val hasEntityIdSLot = slot<Int>()
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(capture(hasEntityIdSLot)) } answers {
			when (hasEntityIdSLot.captured) {
				in 1..6 -> true
				10 -> true
				12 -> true
				else -> false
			}
		}
		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(
				any(),
				any(),
				any()
			)
		} returns true

		coEvery {
			serverProjectApi.downloadEntity(
				any(),
				any(),
				4,
				any(),
				any()
			)
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { hash() } returns "downloaded-hash-4"
			})
		)
		coEvery {
			serverProjectApi.downloadEntity(
				any(),
				any(),
				11,
				any(),
				any()
			)
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 11
				every { hash() } returns "downloaded-hash-11"
			})
		)

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = EntityDeleteOperationState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
			serverSyncData = beganResponse,
			collatedIds = collatedIds,
			maxId = 12,
			newClientIds = listOf(12),
		)

		/**
		 * 1, 3, 4, 11, 12
		 * 1 UPLOAD
		 * 3 UPLOAD / CONFLICT
		 * 4 DOWNLOAD
		 * 11 DOWNLOAD
		 * 12 UPLOAD
		 */

		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		val data = result.data
		assertIs<EntityTransferState>(data)

		coVerify { mockSynchronizers.sceneSynchronizer.uploadEntity(1, any(), any(), any(), any(), any(), any()) }
		coVerify { mockSynchronizers.sceneSynchronizer.uploadEntity(3, any(), any(), any(), any(), any(), any()) }
		coVerify {
			mockSynchronizers.sceneSynchronizer.uploadEntity(
				12,
				any(),
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}

		coVerify { serverProjectApi.downloadEntity(any(), any(), 4, any(), any()) }
		coVerify { serverProjectApi.downloadEntity(any(), any(), 11, any(), any()) }

		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(
				any(),
				any(),
				any()
			)
		} returns true
	}

	/**
	 * Minimal state whose combined ID sequence is a single [downloadId] that the client does
	 * not own — so [EntityTransferOperation] takes the download branch for it, letting each
	 * test drive a specific server-response path.
	 */
	private fun singleDownloadState(
		downloadId: Int,
		onlyNew: Boolean = false,
		newClientIds: List<Int> = emptyList(),
	) = EntityDeleteOperationState(
		onlyNew = onlyNew,
		clientSyncData = ProjectSynchronizationData(
			lastId = downloadId,
			newIds = emptyList(),
			lastSync = Instant.fromEpochSeconds(1),
			dirty = emptyList(),
			deletedIds = emptySet(),
		),
		entityState = ClientEntityState(emptySet()),
		serverProjectId = projId,
		serverSyncData = ProjectSynchronizationBegan(
			syncId = "sync-id",
			lastSync = Instant.fromEpochSeconds(1),
			lastId = downloadId,
			idSequence = listOf(downloadId),
			deletedIds = emptySet(),
		),
		collatedIds = CollateIdsState.CollatedIds(
			combinedDeletions = emptySet(),
			serverDeletedIds = emptySet(),
			newlyDeletedIds = emptySet(),
			dirtyEntities = mutableListOf(),
		),
		maxId = downloadId,
		newClientIds = newClientIds,
	)

	private suspend fun EntityTransferOperation.run(state: EntityDeleteOperationState) = execute(
		state = state,
		onProgress = { _, _ -> },
		onLog = {},
		onConflict = {},
		onComplete = {},
	)

	@Test
	fun `download - server reports not modified, transfer still succeeds`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), any(), 4, any(), any())
		} returns Result.failure(EntityNotModifiedException(4))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
	}

	@Test
	fun `download - entity missing from server and client is deleted remotely`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), any(), 4, any(), any())
		} returns Result.failure(EntityNotFoundException(4))
		coEvery { serverProjectApi.deleteId(any(), any(), 4, any()) } returns
			Result.success(DeleteIdsResponse(deleted = true))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) { serverProjectApi.deleteId(any(), any(), 4, any()) }
	}

	@Test
	fun `download - stale server hash is healed by a forced upload`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Client owns the entity but it's neither dirty nor newer than the server, so it
		// downloads — then the stale-hash response triggers the force-upload heal path.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			serverProjectApi.downloadEntity(any(), any(), 4, any(), any())
		} returns Result.failure(StaleServerHashException(4, "cached", "computed"))
		coEvery {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), any(), any(), any(), true, any())
		} returns true

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), any(), any(), any(), true, any())
		}
	}

	@Test
	fun `download - a failed store logs an error but does not record a synced hash`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { hash() } returns "h-4"
			})
		)
		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any())
		} returns false

		val logs = mutableListOf<SyncLogMessage>()
		val result = op.execute(
			state = singleDownloadState(4),
			onProgress = { _, _ -> },
			onLog = { logs.add(it) },
			onConflict = {},
			onComplete = {},
		)

		assertTrue(isSuccess(result))
		// A failed store is logged but never recorded as the conflict baseline.
		coVerify(exactly = 0) { syncJournal.recordSyncedHash(4, any()) }
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
	}

	@Test
	fun `download - a failed store marks the transfer unsuccessful`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { hash() } returns "h-4"
			})
		)
		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any())
		} returns false

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertFalse(
			assertIs<EntityTransferState>(result.data).allSuccess,
			"A download whose store failed must not report the transfer as fully successful",
		)
	}

	@Test
	fun `onlyNew - uploads each new client entity`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(20) } returns true
		coEvery {
			mockSynchronizers.sceneSynchronizer.uploadEntity(20, any(), any(), any(), any(), any(), any())
		} returns true

		val state = singleDownloadState(20, onlyNew = true, newClientIds = listOf(20))
		val result = op.run(state)

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) {
			mockSynchronizers.sceneSynchronizer.uploadEntity(20, any(), any(), any(), any(), any(), any())
		}
	}
}