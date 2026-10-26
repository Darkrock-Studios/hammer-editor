package synchronizer

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.*
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import getProjectDef
import io.ktor.http.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientProjectSynchronizerTest : BaseTest() {

	private lateinit var strRes: StrRes

	@MockK(relaxed = true)
	private lateinit var syncJournal: SyncJournal

	@MockK(relaxed = true)
	private lateinit var entitySynchronizers: EntitySynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	private lateinit var mockSynchronizers: MockSynchronizers

	private val syncGate = ProjectSyncGateRepository()

	@MockK
	private lateinit var prepareForSyncOperation: PrepareForSyncOperation

	@MockK
	private lateinit var fetchLocalDataOperation: FetchLocalDataOperation

	@MockK
	private lateinit var fetchServerDataOperation: FetchServerDataOperation

	@MockK
	private lateinit var collateIdsOperation: CollateIdsOperation

	@MockK
	private lateinit var backupOperation: BackupOperation

	@MockK
	private lateinit var idConflictResolutionOperation: IdConflictResolutionOperation

	@MockK
	private lateinit var entityDeleteOperation: EntityDeleteOperation

	@MockK
	private lateinit var entityTransferOperation: EntityTransferOperation

	@MockK
	private lateinit var writingActivitySyncOperation: WritingActivitySyncOperation

	@MockK
	private lateinit var projectDataSyncOperation: ProjectDataSyncOperation

	@MockK
	private lateinit var finalizeSyncOperation: FinalizeSyncOperation

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		mockSynchronizers = MockSynchronizers(false)
		strRes = TestStrRes()
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)

				factory { prepareForSyncOperation }
				factory { fetchLocalDataOperation }
				factory { fetchServerDataOperation }
				factory { collateIdsOperation }
				factory { backupOperation }
				factory { idConflictResolutionOperation }
				factory { entityDeleteOperation }
				factory { entityTransferOperation }
				factory { writingActivitySyncOperation }
				factory { projectDataSyncOperation }
				factory { finalizeSyncOperation }
			}
		})
	}

	private fun createProjectSync(projectDef: ProjectDef): ClientProjectSynchronizer {
		configureKoin(projectDef)
		return ClientProjectSynchronizer(
			projectDef = projectDef,
			entitySynchronizers = entitySynchronizers,
			strRes = strRes,
			syncJournal = syncJournal,
			globalSettingsStore = globalSettingsStore,
			serverProjectApi = serverProjectApi,
			syncGate = syncGate,
		)
	}

	private fun succeedEveryOperation(): CResult<SyncOperationState> {
		val result = CResult.success(mockk<SyncOperationState>(relaxed = true))
		listOf(
			prepareForSyncOperation,
			fetchLocalDataOperation,
			fetchServerDataOperation,
			collateIdsOperation,
			backupOperation,
			idConflictResolutionOperation,
			projectDataSyncOperation,
			entityDeleteOperation,
			entityTransferOperation,
			writingActivitySyncOperation,
			finalizeSyncOperation,
		).forEach { operation ->
			coEvery { operation.execute(any(), any(), any(), any(), any()) } returns result
		}
		return result
	}

	private suspend fun ClientProjectSynchronizer.syncQuietly(): Boolean = sync(
		onProgress = { _, _ -> },
		onLog = {},
		onConflict = mockk(relaxed = true),
		onComplete = {},
		onUnauthorized = {},
	)

	@Test
	fun `Sync waits for a write in progress on the project`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val syncer = createProjectSync(projectDef)
		succeedEveryOperation()
		assertTrue(syncGate.beginWrite(projectDef))

		val sync = async { syncer.syncQuietly() }
		runCurrent()
		coVerify(exactly = 0) { prepareForSyncOperation.execute(any(), any(), any(), any(), any()) }

		syncGate.endWrite(projectDef)
		assertTrue(sync.await())
		coVerify(exactly = 1) { prepareForSyncOperation.execute(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `A write is refused while the project syncs, and admitted after`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val syncer = createProjectSync(projectDef)
		val result = succeedEveryOperation()
		var admittedDuringSync: Boolean? = null
		coEvery { entityTransferOperation.execute(any(), any(), any(), any(), any()) } coAnswers {
			admittedDuringSync = syncGate.beginWrite(projectDef)
			result
		}

		assertTrue(syncer.syncQuietly())

		assertEquals(false, admittedDuringSync)
		assertTrue(syncGate.beginWrite(projectDef))
	}

	@Test
	fun `Successful Sync`() = runTest {
		val syncer = createProjectSync(getProjectDef(PROJECT_2_NAME))

		val result = CResult.success(mockk<SyncOperationState>(relaxed = true))
		coEvery {
			prepareForSyncOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchLocalDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery { collateIdsOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery { backupOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery { entityDeleteOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery {
			entityTransferOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			writingActivitySyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery {
			projectDataSyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) } returns result

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val success = syncer.sync(
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
			onUnauthorized = {}
		)
		assertTrue(success)

		coVerify(exactly = 1) { prepareForSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { fetchLocalDataOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 1) { collateIdsOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { backupOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 1) { projectDataSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { entityDeleteOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { entityTransferOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { writingActivitySyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Sync fails on Backup Operation`() = runTest {
		val syncer = createProjectSync(getProjectDef(PROJECT_2_NAME))

		val result = CResult.success(mockk<SyncOperationState>(relaxed = true))
		val failure = CResult.failure<SyncOperationState>(Exception("Test failure"))
		coEvery {
			prepareForSyncOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchLocalDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery { collateIdsOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery { backupOperation.execute(any(), any(), any(), any(), any()) } returns failure
		coEvery {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery { entityDeleteOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery {
			entityTransferOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			writingActivitySyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery {
			projectDataSyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) } returns result

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val success = syncer.sync(
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
			onUnauthorized = {}
		)
		assertFalse(success)

		coVerify(exactly = 1) { prepareForSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { fetchLocalDataOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 1) { collateIdsOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { backupOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 0) { projectDataSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { entityDeleteOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { entityTransferOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { writingActivitySyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Sync fails with 401 Unauthorized and calls onUnauthorized`() = runTest {
		val syncer = createProjectSync(getProjectDef(PROJECT_2_NAME))

		val result = CResult.success(mockk<SyncOperationState>(relaxed = true))
		val unauthorizedFailure = CResult.failure<SyncOperationState>(
			HttpFailureException(
				statusCode = HttpStatusCode.Unauthorized,
				error = HttpResponseError(
					error = "Unauthorized",
					displayMessage = "Unauthorized access"
				)
			)
		)

		coEvery {
			prepareForSyncOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchLocalDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns unauthorizedFailure
		coEvery { collateIdsOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery { backupOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery { entityDeleteOperation.execute(any(), any(), any(), any(), any()) } returns result
		coEvery {
			entityTransferOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns result
		coEvery {
			writingActivitySyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery {
			projectDataSyncOperation.execute(any(), any(), any(), any(), any())
		} returns result
		coEvery { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) } returns result

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)
		val onUnauthorized = mockk<suspend () -> Unit>(relaxed = true)

		val success = syncer.sync(
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
			onUnauthorized = onUnauthorized
		)
		assertFalse(success)

		coVerify(exactly = 1) { prepareForSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { fetchLocalDataOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) {
			fetchServerDataOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 0) { collateIdsOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { backupOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) {
			idConflictResolutionOperation.execute(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		}
		coVerify(exactly = 0) { projectDataSyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { entityDeleteOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { entityTransferOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { writingActivitySyncOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) }

		coVerify(exactly = 1) { onUnauthorized() }
	}
}
