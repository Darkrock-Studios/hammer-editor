package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import utils.sharedFlow
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FetchServerDataOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

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

	private fun createOperation(projectDef: ProjectDef): FetchServerDataOperation {
		configureKoin(projectDef)
		return FetchServerDataOperation(
			projectDef = projectDef,
			strRes = strRes,
			projectMetadataDatasource = projectMetadataDatasource,
			globalSettingsStore = globalSettingsStore,
			serverProjectApi = serverProjectApi,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery {
			serverProjectApi.beginProjectSync(
				any(),
				any(),
				any(),
				any()
			)
		} returns Result.success(beganResponse)
		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
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
		assertIs<FetchServerDataState>(data)

		assertEquals(
			FetchServerDataState(
				onlyNew = false,
				clientSyncData = projectData,
				entityState = entityState,
				serverProjectId = projId,
				serverSyncData = beganResponse
			),
			data
		)
		// The request carries the settings userId, the metadata project id, and the local entity state.
		coVerify { serverProjectApi.beginProjectSync(1L, projId, entityState, false) }
	}

	@Test
	fun `Handle Failure`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata
		coEvery {
			serverProjectApi.beginProjectSync(
				any(),
				any(),
				any(),
				any()
			)
		} returns Result.failure(Exception("Test Failure"))

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
		)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertFalse(isSuccess(result))
	}
}