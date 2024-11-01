package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityTransferState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import utils.TestClock
import utils.TestStrRes
import utils.sharedFlow
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FinalizeSyncOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsRepository: GlobalSettingsRepository

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var syncDataDatasource: SyncDataDatasource

	private lateinit var strRes: TestStrRes

	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		strRes = TestStrRes()
		mockSynchronizers = MockSynchronizers(false)
		clock = TestClock(Clock.System)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)
			}
		})
	}

	private fun createOperation(projectDef: ProjectDef): FinalizeSyncOperation {
		configureKoin(projectDef)
		return FinalizeSyncOperation(
			projectDef = projectDef,
			serverProjectApi = serverProjectApi,
			strRes = strRes,
			entitySynchronizers = EntitySynchronizers(projectDef),
			clock = clock,
			globalSettingsRepository = globalSettingsRepository,
			syncDataDatasource = syncDataDatasource,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { globalSettingsRepository.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coEvery { synchronizer.finalizeSync() } just Runs
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = EntityTransferState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
			serverSyncData = beganResponse,
			collatedIds = collatedIds,
			maxId = 12,
			newClientIds = listOf(12),
			allSuccess = true,
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
		assertIs<EntityTransferState>(data)

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coVerify { synchronizer.finalizeSync() }
		}
		coVerify { serverProjectApi.endProjectSync(any(), any(), any(), any(), any(), any()) }
		coVerify { syncDataDatasource.saveSyncData(any()) }
	}
}