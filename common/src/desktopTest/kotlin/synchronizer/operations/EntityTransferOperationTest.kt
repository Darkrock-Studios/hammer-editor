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
import kotlin.test.assertEquals
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
			mockSynchronizers.sceneSynchronizer.uploadEntity(any(), any(), any(), any(), any(), any(), any())
		} returns true

		coEvery {
			serverProjectApi.downloadEntity(
				any(),
				4,
				any(),
				any()
			)
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.SCENE
				every { hash() } returns "downloaded-hash-4"
			})
		)
		coEvery {
			serverProjectApi.downloadEntity(
				any(),
				11,
				any(),
				any()
			)
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 11
				every { type } returns ApiProjectEntity.Type.SCENE
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
		assertTrue(data.allSuccess)
		// Successfully uploaded dirty entities (1, 3) are cleared; 9 (deleted) and 11
		// (downloaded, not uploaded) remain for later handling.
		assertEquals(produceEntityStateList(9, 11), data.collatedIds.dirtyEntities)

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

		coVerify { serverProjectApi.downloadEntity(any(), 4, any(), any()) }
		coVerify { serverProjectApi.downloadEntity(any(), 11, any(), any()) }

		// The downloaded entities are actually persisted locally.
		val stored = mutableListOf<ApiProjectEntity.SceneEntity>()
		coVerify { mockSynchronizers.sceneSynchronizer.storeEntity(capture(stored), any(), any()) }
		assertEquals(setOf(4, 11), stored.map { it.id }.toSet())
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
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(EntityNotModifiedException(4))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
	}

	@Test
	fun `download - entity missing from server and client is deleted remotely`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(EntityNotFoundException(4))
		coEvery { serverProjectApi.deleteId(any(), 4, any()) } returns
			Result.success(DeleteIdsResponse(deleted = true))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) { serverProjectApi.deleteId(any(), 4, any()) }
	}

	@Test
	fun `download - stale server hash is healed by a forced upload`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Client owns the entity but it's neither dirty nor newer than the server, so it
		// downloads — then the stale-hash response triggers the force-upload heal path.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
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
	fun `download - stale server hash with no local copy fails instead of claiming healed`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Fresh device: no synchronizer owns the entity, so there is nothing to force-upload.
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(StaleServerHashException(4, "cached", "computed"))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 0) {
			mockSynchronizers.sceneSynchronizer.uploadEntity(any(), any(), any(), any(), any(), any(), any())
		}
	}

	@Test
	fun `download - a failed store logs an error but does not record a synced hash`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.SCENE
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
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.SCENE
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
	fun `download - server returns a different id than requested is rejected`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Requested id 4, but a hostile server answers with a forged entity carrying id 7.
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 7
				every { type } returns ApiProjectEntity.Type.SCENE
				every { hash() } returns "forged-hash-7"
			})
		)

		val logs = mutableListOf<SyncLogMessage>()
		val result = op.execute(
			state = singleDownloadState(4),
			onProgress = { _, _ -> },
			onLog = { logs.add(it) },
			onConflict = {},
			onComplete = {},
		)

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
		// The forged entity is never stored and never poisons the conflict baseline.
		coVerify(exactly = 0) { mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any()) }
		coVerify(exactly = 0) { syncJournal.recordSyncedHash(7, any()) }
		coVerify(exactly = 0) { syncJournal.recordSyncedHash(4, any()) }
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
	}

	@Test
	fun `download - server returns a different type than the client owns is rejected`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// The client already owns id 4 as a Scene...
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		// ...but the server steers the same id into a Note, attempting a type confusion.
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.NoteEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.NOTE
				every { hash() } returns "wrong-type-hash-4"
			})
		)

		val logs = mutableListOf<SyncLogMessage>()
		val result = op.execute(
			state = singleDownloadState(4),
			onProgress = { _, _ -> },
			onLog = { logs.add(it) },
			onConflict = {},
			onComplete = {},
		)

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
		// Neither repository is written, and no hash is recorded for the contested id.
		coVerify(exactly = 0) { mockSynchronizers.noteSynchronizer.storeEntity(any(), any(), any()) }
		coVerify(exactly = 0) { mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any()) }
		coVerify(exactly = 0) { syncJournal.recordSyncedHash(4, any()) }
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
	}

	@Test
	fun `download - a new entity the client does not own is stored and its hash recorded`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// No synchronizer owns id 4 (findEntityType == null), so the type check must allow it.
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.SCENE
				every { hash() } returns "new-hash-4"
			})
		)
		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any())
		} returns true

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) { mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any()) }
		coVerify(exactly = 1) { syncJournal.recordSyncedHash(4, "new-hash-4") }
	}

	@Test
	fun `download - matching id and owned type is stored and its hash recorded`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Client owns id 4 as a Scene and the server agrees on both id and type.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.success(
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity> {
				every { id } returns 4
				every { type } returns ApiProjectEntity.Type.SCENE
				every { hash() } returns "matching-hash-4"
			})
		)
		coEvery {
			mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any())
		} returns true

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) { mockSynchronizers.sceneSynchronizer.storeEntity(any(), any(), any()) }
		coVerify(exactly = 1) { syncJournal.recordSyncedHash(4, "matching-hash-4") }
	}

	@Test
	fun `download - entity missing from server but present locally is re-uploaded to heal`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Owned locally, but not dirty/new and not newer than the server, so it takes the
		// download branch — then the server reports it gone while we still hold a copy.
		// Known deletions never reach this point, so the local copy is the surviving truth.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), null, any(), any(), false, any())
		} returns true

		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(EntityNotFoundException(4))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 1) {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), null, any(), any(), false, any())
		}
		coVerify(exactly = 0) { serverProjectApi.deleteId(any(), 4, any()) }
	}

	@Test
	fun `download - a generic server failure marks the transfer unsuccessful`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(RuntimeException("network down"))

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
	}

	@Test
	fun `download - a failed stale-hash heal marks the transfer unsuccessful`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(StaleServerHashException(4, "cached", "computed"))
		coEvery {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), any(), any(), any(), true, any())
		} returns false

		val result = op.run(singleDownloadState(4))

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
	}

	@Test
	fun `upload - a failed entity upload marks the transfer unsuccessful`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Owned and newly created, so it takes the upload branch — which then fails.
		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(4) } returns true
		coEvery {
			mockSynchronizers.sceneSynchronizer.uploadEntity(4, any(), any(), any(), any(), any(), any())
		} returns false

		val result = op.run(singleDownloadState(4, newClientIds = listOf(4)))

		assertTrue(isSuccess(result))
		assertFalse(assertIs<EntityTransferState>(result.data).allSuccess)
	}

	@Test
	fun `onlyNew - an unowned new id is skipped as a successful no-op`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// No synchronizer owns 99, so the upload helper logs a warning and skips it.

		val result = op.run(singleDownloadState(99, onlyNew = true, newClientIds = listOf(99)))

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		coVerify(exactly = 0) {
			mockSynchronizers.sceneSynchronizer.uploadEntity(99, any(), any(), any(), any(), any(), any())
		}
	}

	@Test
	fun `download - a failed remote delete during cleanup still completes the transfer`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		// Missing from both server and client, so it's cleaned up remotely — but the delete fails.
		coEvery {
			serverProjectApi.downloadEntity(any(), 4, any(), any())
		} returns Result.failure(EntityNotFoundException(4))
		coEvery { serverProjectApi.deleteId(any(), 4, any()) } returns
			Result.failure(RuntimeException("delete rejected"))

		val logs = mutableListOf<SyncLogMessage>()
		val result = op.execute(
			state = singleDownloadState(4),
			onProgress = { _, _ -> },
			onLog = { logs.add(it) },
			onConflict = {},
			onComplete = {},
		)

		assertTrue(isSuccess(result))
		assertTrue(assertIs<EntityTransferState>(result.data).allSuccess)
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
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