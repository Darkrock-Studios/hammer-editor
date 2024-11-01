package synchronizer

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.BackupOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.CollateIdsOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityDeleteOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchLocalDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
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
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientProjectSynchronizerTest : BaseTest() {

	private lateinit var strRes: StrRes

	@MockK(relaxed = true)
	private lateinit var syncDataRepository: SyncDataRepository

	@MockK(relaxed = true)
	private lateinit var entitySynchronizers: EntitySynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsRepository: GlobalSettingsRepository

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	private lateinit var mockSynchronizers: MockSynchronizers

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
			syncDataRepository = syncDataRepository,
			globalSettingsRepository = globalSettingsRepository,
			projectMetadataDatasource = projectMetadataDatasource,
			serverProjectApi = serverProjectApi,
		)
	}

	@Test
	fun `Initialize`() = runTest {
		val syncer = createProjectSync(getProjectDef(PROJECT_2_NAME))
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
		coEvery { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) } returns result

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val success = syncer.sync(
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
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
		coVerify(exactly = 1) { entityDeleteOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { entityTransferOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Sync fails on FetchServerData Operation`() = runTest {
		val syncer = createProjectSync(getProjectDef(PROJECT_2_NAME))

		coEvery { projectMetadataDatasource.requireProjectId(any()) } returns ProjectId("project-id")

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
		coEvery { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) } returns result

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val success = syncer.sync(
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
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
		coVerify(exactly = 0) { entityDeleteOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { entityTransferOperation.execute(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { finalizeSyncOperation.execute(any(), any(), any(), any(), any()) }
	}
}