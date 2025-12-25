package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.InitialSyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EnsureProjectIdOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
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
import synchronizer.MockSynchronizers
import synchronizer.addSynchronizers
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EnsureProjectIdOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var serverProjectsApi: ServerProjectsApi

	@MockK(relaxed = true)
	private lateinit var projectsRepository: ProjectsRepository

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

	private fun createOperation(projectDef: ProjectDef): EnsureProjectIdOperation {
		configureKoin(projectDef)
		return EnsureProjectIdOperation(
			projectDef = projectDef,
			projectMetadataDatasource = projectMetadataDatasource,
			serverProjectsApi = serverProjectsApi,
			projectsRepository = projectsRepository,
			strRes = strRes,
		)
	}

	@Test
	fun `Project ID already exists - No-op`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		val metadataWithId = ProjectMetadata(
			info = Info(
				created = Instant.fromEpochSeconds(123456),
				lastAccessed = null,
				dataVersion = PROJECT_DATA_VERSION,
				serverProjectId = ProjectId("existing-id")
			)
		)
		coEvery { projectMetadataDatasource.loadMetadata(projectDef) } returns metadataWithId

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
		coVerify(exactly = 0) { serverProjectsApi.beginProjectsSync() }
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
	}

	@Test
	fun `Project ID missing - Created Successfully`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		val metadataWithoutId = ProjectMetadata(
			info = Info(
				created = Instant.fromEpochSeconds(123456),
				lastAccessed = null,
				dataVersion = PROJECT_DATA_VERSION,
				serverProjectId = null
			)
		)
		coEvery { projectMetadataDatasource.loadMetadata(projectDef) } returns metadataWithoutId

		val syncId = "test-sync-id"
		val newProjectId = ProjectId("new-project-id")

		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(
			BeginProjectsSyncResponse(syncId, emptySet(), emptySet())
		)
		coEvery { serverProjectsApi.createProject(projectDef.name, syncId) } returns Result.success(
			CreateProjectResponse(newProjectId, false)
		)
		coEvery { serverProjectsApi.endProjectsSync(syncId) } returns Result.success("Success")

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
		coVerify { serverProjectsApi.beginProjectsSync() }
		coVerify { serverProjectsApi.createProject(projectDef.name, syncId) }
		coVerify { projectsRepository.setProjectId(projectDef, newProjectId) }
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
	}

	@Test
	fun `Project ID missing - Server Creation Fails`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		val metadataWithoutId = ProjectMetadata(
			info = Info(
				created = Instant.fromEpochSeconds(123456),
				lastAccessed = null,
				dataVersion = PROJECT_DATA_VERSION,
				serverProjectId = null
			)
		)
		coEvery { projectMetadataDatasource.loadMetadata(projectDef) } returns metadataWithoutId

		val syncId = "test-sync-id"
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(
			BeginProjectsSyncResponse(syncId, emptySet(), emptySet())
		)
		coEvery { serverProjectsApi.createProject(projectDef.name, syncId) } returns Result.failure(
			Exception("Server error")
		)

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

		assertFalse(isSuccess(result))
		coVerify { onLog(any()) }
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
	}

	@Test
	fun `Project ID missing - Begin Sync Fails`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		val metadataWithoutId = ProjectMetadata(
			info = Info(
				created = Instant.fromEpochSeconds(123456),
				lastAccessed = null,
				dataVersion = PROJECT_DATA_VERSION,
				serverProjectId = null
			)
		)
		coEvery { projectMetadataDatasource.loadMetadata(projectDef) } returns metadataWithoutId

		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.failure(
			Exception("Begin sync error")
		)

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

		assertFalse(isSuccess(result))
		coVerify { onLog(any()) }
		coVerify(exactly = 0) { serverProjectsApi.createProject(any(), any()) }
	}
}
