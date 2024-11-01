package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.CollateIdsState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizers
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.IdConflictResolutionOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdConflictResolutionOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var idRepository: IdRepository

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

	private fun createOperation(projectDef: ProjectDef): IdConflictResolutionOperation {
		configureKoin(projectDef)
		return IdConflictResolutionOperation(
			projectDef = projectDef,
			idRepository = idRepository,
			entitySynchronizers = EntitySynchronizers(projectDef),
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idRepository.findNextId() } just Runs
		coEvery { idRepository.peekLastId() } returns 11

		coEvery { mockSynchronizers.sceneSynchronizer.ownsEntity(11) } returns true
		coEvery { mockSynchronizers.sceneSynchronizer.deleteEntityLocal(any(), any()) } just Runs
		coEvery { mockSynchronizers.sceneSynchronizer.reIdEntity(any(), any()) } just Runs

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = CollateIdsState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
			serverSyncData = beganResponse,
			collatedIds = collatedIds,
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
		assertIs<IdConflictResolutionState>(data)

		assertEquals(12, data.maxId)
		assertEquals(listOf(12), data.newClientIds)
		assertEquals(
			produceEntityStateList(1, 3) + EntityOriginalState(12, "old-hash-11"),
			data.clientSyncData.dirty
		)

		coVerify { mockSynchronizers.sceneSynchronizer.deleteEntityLocal(11, any()) }
		coVerify { mockSynchronizers.sceneSynchronizer.reIdEntity(11, 12) }
	}
}