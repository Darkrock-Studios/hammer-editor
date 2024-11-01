package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.LoadEntityResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityDeleteOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityTransferState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityTransferOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntityTransferOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = false)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

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
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity>())
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
			LoadEntityResponse(mockk<ApiProjectEntity.SceneEntity>())
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

		coVerify { mockSynchronizers.sceneSynchronizer.uploadEntity(1, any(), any(), any(), any()) }
		coVerify { mockSynchronizers.sceneSynchronizer.uploadEntity(3, any(), any(), any(), any()) }
		coVerify {
			mockSynchronizers.sceneSynchronizer.uploadEntity(
				12,
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
}