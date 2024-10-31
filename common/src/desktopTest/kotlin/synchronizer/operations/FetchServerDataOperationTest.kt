package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchLocalDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.FetchServerDataState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
	private lateinit var globalSettingsRepository: GlobalSettingsRepository

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
			globalSettingsRepository = globalSettingsRepository,
			serverProjectApi = serverProjectApi,
		)
	}

	val projectData = ProjectSynchronizationData(
		lastId = 10,
		newIds = listOf(10),
		lastSync = Instant.fromEpochSeconds(123456),
		dirty = produceEntityStateList(1, 3),
		deletedIds = setOf(8, 9)
	)

	val entityState = ClientEntityState(produceEntityHashSet(1, 2, 3, 4, 5, 6, 7, 10))
	val projId = ProjectId("project-id")

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		val beganResponse = ProjectSynchronizationBegan(
			syncId = "sync-id",
			lastSync = Instant.fromEpochSeconds(1234567),
			lastId = 11,
			idSequence = listOf(1, 4, 5),
			deletedIds = setOf(8, 9, 11),
		)
		coEvery {
			serverProjectApi.beginProjectSync(
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} returns Result.success(beganResponse)
		coEvery { globalSettingsRepository.serverSettingsUpdates } returns sharedFlow {
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
	}

	@Test
	fun `Handle Failure`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { globalSettingsRepository.serverSettingsUpdates } returns sharedFlow {
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