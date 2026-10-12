package synchronizer.operations

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.InitialSyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectCreateResolver
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncOperationState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EnsureProjectOnServerOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectAdoptConflictException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDeletedElsewhereException
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import getProjectDef
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class EnsureProjectOnServerOperationTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)
	private val localId = ProjectId("local-id")
	private val syncId = "test-sync-id"

	private lateinit var syncDataDatasource: SyncDataDatasource
	private lateinit var serverProjectsApi: ServerProjectsApi
	private lateinit var projectsRepository: ProjectsRepository
	private val logs = mutableListOf<SyncLogMessage>()

	@BeforeEach
	override fun setup() {
		super.setup()
		syncDataDatasource = mockk()
		serverProjectsApi = mockk()
		projectsRepository = mockk(relaxed = true)
		logs.clear()

		every { projectsRepository.getProjectId(projectDef) } returns localId
		every { projectsRepository.findProject(any<ProjectId>()) } returns null
		coEvery { syncDataDatasource.loadSyncDataOrNull() } returns null
		coEvery { serverProjectsApi.beginProjectsSync() } returns
			Result.success(BeginProjectsSyncResponse(syncId, emptySet(), emptySet()))
		coEvery { serverProjectsApi.endProjectsSync(syncId) } returns Result.success("ok")
	}

	private suspend fun execute(): CResult<SyncOperationState> =
		EnsureProjectOnServerOperation(
			projectDef = projectDef,
			syncDataDatasource = syncDataDatasource,
			projectCreateResolver = ProjectCreateResolver(serverProjectsApi, projectsRepository),
			strRes = TestStrRes(),
		).execute(
			state = InitialSyncOperationState(false),
			onProgress = mockk(relaxed = true),
			onLog = { logs.add(it) },
			onConflict = mockk(relaxed = true),
			onComplete = mockk(relaxed = true),
		)

	private fun conflict(errorCode: String) = HttpFailureException(
		statusCode = HttpStatusCode.Conflict,
		error = HttpResponseError(error = "Conflict", displayMessage = "nope", errorCode = errorCode),
	)

	@Test
	fun `A project that has synced before skips the create`() = runTest {
		coEvery { syncDataDatasource.loadSyncDataOrNull() } returns
			projectData.copy(lastSync = Instant.fromEpochSeconds(123456))

		val result = execute()

		assertTrue(isSuccess(result))
		coVerify(exactly = 0) { serverProjectsApi.beginProjectsSync() }
	}

	@Test
	fun `A never-synced project is created under its own id`() = runTest {
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.success(CreateProjectResponse(localId, alreadyExisted = false))

		val result = execute()

		assertTrue(isSuccess(result))
		coVerify { serverProjectsApi.createProject(projectDef.name, localId, syncId) }
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
	}

	@Test
	fun `A journal that never completed a sync still creates`() = runTest {
		coEvery { syncDataDatasource.loadSyncDataOrNull() } returns
			projectData.copy(lastSync = Instant.DISTANT_PAST)
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.success(CreateProjectResponse(localId, alreadyExisted = true))

		val result = execute()

		assertTrue(isSuccess(result))
		coVerify { serverProjectsApi.createProject(projectDef.name, localId, syncId) }
	}

	@Test
	fun `A same-named server project is adopted`() = runTest {
		val serverId = ProjectId("server-id")
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.success(CreateProjectResponse(serverId, alreadyExisted = true))

		val result = execute()

		assertTrue(isSuccess(result))
		coVerify { projectsRepository.setProjectId(projectDef, serverId) }
	}

	@Test
	fun `A same-named server project another local project holds is not adopted`() = runTest {
		val heldId = ProjectId("held-id")
		val holder = getProjectDef(PROJECT_1_NAME)
		every { projectsRepository.findProject(heldId) } returns holder
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.success(CreateProjectResponse(heldId, alreadyExisted = true))

		val result = execute()

		assertFalse(isSuccess(result))
		assertIs<ProjectAdoptConflictException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
	}

	@Test
	fun `A project deleted elsewhere fails the sync without retrying`() = runTest {
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.failure(conflict(ApiErrorCode.PROJECT_DELETED))

		val result = execute()

		assertFalse(isSuccess(result))
		assertIs<ProjectDeletedElsewhereException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 1) { serverProjectsApi.createProject(any(), any(), any()) }
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
	}

	@Test
	fun `Server creation failure fails the sync and ends the session`() = runTest {
		coEvery { serverProjectsApi.createProject(projectDef.name, localId, syncId) } returns
			Result.failure(Exception("Server error"))

		val result = execute()

		assertFalse(isSuccess(result))
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
		coVerify { serverProjectsApi.endProjectsSync(syncId) }
	}

	@Test
	fun `Begin sync failure fails without creating`() = runTest {
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.failure(Exception("Begin sync error"))

		val result = execute()

		assertFalse(isSuccess(result))
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
		coVerify(exactly = 0) { serverProjectsApi.createProject(any(), any(), any()) }
	}
}
