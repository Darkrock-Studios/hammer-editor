package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.PrepareForSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrepareForSyncOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK
	private lateinit var idRepository: IdRepository

	@MockK(relaxed = true)
	private lateinit var syncDataRepository: SyncDataRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

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

	private fun createOperation(projectDef: ProjectDef): PrepareForSyncOperation {
		configureKoin(projectDef)
		return PrepareForSyncOperation(
			projectDef = projectDef,
			entitySynchronizers = EntitySynchronizers(projectDef),
			idRepository = idRepository,
			syncDataRepository = syncDataRepository,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))
		coEvery { idRepository.findNextId() } just Runs
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coEvery { synchronizer.prepareForSync() } just Runs
		}

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend () -> Unit>(relaxed = true)

		val initialState = InitialSyncOperationState(false)
		val result = op.execute(
			state = initialState,
			onProgress = onProgress,
			onLog = onLog,
			onConflict = onConflict,
			onComplete = onComplete,
		)

		assertTrue(isSuccess(result))
		assertIs<SyncOperationState>(result.data)

		coVerify { idRepository.findNextId() }
		coVerify { syncDataRepository.createSyncData() }

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coVerify { synchronizer.prepareForSync() }
		}
	}
}