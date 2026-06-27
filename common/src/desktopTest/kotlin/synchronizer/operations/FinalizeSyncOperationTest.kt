package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
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
import utils.sharedFlow
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class FinalizeSyncOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var syncDataDatasource: SyncDataDatasource

	@MockK(relaxed = true)
	private lateinit var projectDataDatasource: ProjectDataDatasource

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
			globalSettingsStore = globalSettingsStore,
			syncDataDatasource = syncDataDatasource,
			projectDataDatasource = projectDataDatasource,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coEvery { synchronizer.finalizeSync() } just Runs
			coEvery { synchronizer.hashEntities(any()) } returns emptySet()
		}
		coEvery { projectDataDatasource.load() } returns StoredProjectData()

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
		coVerify { serverProjectApi.endProjectSync(any(), any(), any(), any(), any()) }
		coVerify { syncDataDatasource.saveSyncData(any()) }
	}
}