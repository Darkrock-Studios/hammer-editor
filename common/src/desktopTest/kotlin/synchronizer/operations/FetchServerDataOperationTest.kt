package synchronizer.operations

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.CreateProjectResponse
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectAdoptConflictException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDeletedElsewhereException
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectNeedsAccountSyncException
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import getProjectDef
import io.ktor.http.HttpStatusCode
import io.mockk.just
import io.mockk.Runs
import io.mockk.coVerifyOrder
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
import kotlin.time.Instant

class FetchServerDataOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectsRepository: ProjectsRepository

	@MockK(relaxed = true)
	private lateinit var projectDataRepository: ProjectDataRepository

	@MockK
	private lateinit var projectCreateResolver: ProjectCreateResolver

	@MockK
	private lateinit var syncJournal: SyncJournal

	private lateinit var strRes: TestStrRes

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)
		every { syncJournal.clearSyncBaseline() } just Runs

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

	private fun createOperation(
		projectDef: ProjectDef,
		resolver: ProjectCreateResolver = projectCreateResolver,
	): FetchServerDataOperation {
		configureKoin(projectDef)
		return FetchServerDataOperation(
			projectDef = projectDef,
			strRes = strRes,
			globalSettingsStore = globalSettingsStore,
			projectCreateResolver = resolver,
			projectDataRepository = projectDataRepository,
			syncJournal = syncJournal,
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

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
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
				serverSyncData = beganResponse
			),
			data
		)
		// The request carries the settings userId, the project id, and the local entity state.
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
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
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

	@Test
	fun `A generic failure does not recreate the project`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns
			Result.failure(Exception("Test Failure"))

		val result = execute(op)

		assertFalse(isSuccess(result))
		// Only a 410 proves the server lacks the project; a transient failure must not create it.
		coVerify(exactly = 0) { projectCreateResolver.createInOwnSession(any()) }
	}

	@Test
	fun `A 410 Gone recreates the project under its id and retries with a cleared baseline`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)
		val clearedJournal = projectData.copy(lastSync = Instant.DISTANT_PAST, syncedHashes = emptyMap())

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returnsMany
			listOf(Result.failure(gone()), Result.success(beganResponse))
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns
			ServerCreateOutcome.Created(alreadyExisted = false, reminted = false)
		coEvery { syncJournal.loadSyncData() } returns clearedJournal

		val result = execute(op)

		assertTrue(isSuccess(result))
		val data = assertIs<FetchServerDataState>(result.data)
		// The old baseline described a server copy that no longer exists.
		coVerifyOrder {
			syncJournal.clearSyncBaseline()
			projectDataRepository.clearSyncBaseline()
		}
		assertEquals(clearedJournal, data.clientSyncData)
		coVerify(exactly = 2) { serverProjectApi.beginProjectSync(1L, projId, entityState, false) }
	}

	@Test
	fun `A 410 whose create needs account sync fails without retrying`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns ServerCreateOutcome.NeedsAccountSync

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertIs<ProjectNeedsAccountSyncException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
	}

	@Test
	fun `A 410 for a project deleted elsewhere fails without retrying`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns ServerCreateOutcome.DeletedElsewhere
		val logs = mutableListOf<SyncLogMessage>()

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertIs<ProjectDeletedElsewhereException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
	}

	@Test
	fun `A second 410 fails rather than looping`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns
			ServerCreateOutcome.Created(alreadyExisted = false, reminted = false)
		coEvery { syncJournal.loadSyncData() } returns projectData

		val result = execute(op)

		assertFalse(isSuccess(result))
		coVerify(exactly = 1) { projectCreateResolver.createInOwnSession(any()) }
		coVerify(exactly = 2) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
	}

	@Test
	fun `A 410 for a never-synced project creates it under its own id`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val serverProjectsApi = stubResolverSession()
		val op = createOperation(projectDef, ProjectCreateResolver(serverProjectsApi, projectsRepository))
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returnsMany
			listOf(Result.failure(gone()), Result.success(beganResponse))
		coEvery { serverProjectsApi.createProject(projectDef.name, projId, SYNC_ID) } returns
			Result.success(CreateProjectResponse(projId, alreadyExisted = false))
		coEvery { syncJournal.loadSyncData() } returns projectData

		val result = execute(op) { logs.add(it) }

		assertTrue(isSuccess(result))
		assertTrue(logs.any { it.level == SyncLogLevel.INFO })
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(SYNC_ID) }
	}

	@Test
	fun `A 410 matching a same-named server project fails for account sync to adopt it`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val serverProjectsApi = stubResolverSession()
		val op = createOperation(projectDef, ProjectCreateResolver(serverProjectsApi, projectsRepository))
		val serverId = ProjectId("server-id")
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { serverProjectsApi.createProject(projectDef.name, projId, SYNC_ID) } returns
			Result.success(CreateProjectResponse(serverId, alreadyExisted = true))

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertIs<ProjectNeedsAccountSyncException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(SYNC_ID) }
	}

	@Test
	fun `A 410 whose id another account holds fails without re-minting`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val serverProjectsApi = stubResolverSession()
		val op = createOperation(projectDef, ProjectCreateResolver(serverProjectsApi, projectsRepository))
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { serverProjectsApi.createProject(projectDef.name, projId, SYNC_ID) } returns
			Result.failure(conflict(ApiErrorCode.PROJECT_ID_TAKEN))

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertIs<ProjectNeedsAccountSyncException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 1) { serverProjectsApi.createProject(any(), any(), any()) }
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(SYNC_ID) }
	}

	@Test
	fun `A 410 does not adopt a server id another local project holds`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val serverProjectsApi = stubResolverSession()
		val op = createOperation(projectDef, ProjectCreateResolver(serverProjectsApi, projectsRepository))
		val heldId = ProjectId("held-id")
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		every { projectsRepository.findProject(heldId) } returns getProjectDef(PROJECT_1_NAME)
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { serverProjectsApi.createProject(projectDef.name, projId, SYNC_ID) } returns
			Result.success(CreateProjectResponse(heldId, alreadyExisted = true))

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertIs<ProjectAdoptConflictException>(result.exception)
		assertTrue(logs.any { it.level == SyncLogLevel.WARN })
		coVerify(exactly = 0) { projectsRepository.setProjectId(any(), any()) }
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(SYNC_ID) }
	}

	@Test
	fun `A 410 whose create fails fails the sync and ends the session`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val serverProjectsApi = stubResolverSession()
		val op = createOperation(projectDef, ProjectCreateResolver(serverProjectsApi, projectsRepository))
		val logs = mutableListOf<SyncLogMessage>()

		stubSettings()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { serverProjectsApi.createProject(projectDef.name, projId, SYNC_ID) } returns
			Result.failure(Exception("Server error"))

		val result = execute(op) { logs.add(it) }

		assertFalse(isSuccess(result))
		assertTrue(logs.any { it.level == SyncLogLevel.ERROR })
		coVerify(exactly = 0) { projectDataRepository.clearSyncBaseline() }
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify { serverProjectsApi.endProjectsSync(SYNC_ID) }
	}

	private fun stubResolverSession(): ServerProjectsApi {
		every { projectsRepository.findProject(any<ProjectId>()) } returns null
		return mockk<ServerProjectsApi>().also { api ->
			coEvery { api.beginProjectsSync() } returns
				Result.success(BeginProjectsSyncResponse(SYNC_ID, emptySet(), emptySet()))
			coEvery { api.endProjectsSync(SYNC_ID) } returns Result.success("ok")
		}
	}

	private fun conflict(errorCode: String) = HttpFailureException(
		statusCode = HttpStatusCode.Conflict,
		error = HttpResponseError(error = "Conflict", displayMessage = "nope", errorCode = errorCode),
	)

	private fun gone() = HttpFailureException(
		statusCode = HttpStatusCode.Gone,
		error = HttpResponseError(error = "Project Not Found", displayMessage = "Project does not exist"),
	)

	private suspend fun stubSettings() {
		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
	}

	private suspend fun execute(
		op: FetchServerDataOperation,
		onLog: OnSyncLog = mockk(relaxed = true),
	) = op.execute(
		state = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
		),
		onProgress = mockk(relaxed = true),
		onLog = onLog,
		onConflict = mockk(relaxed = true),
		onComplete = mockk(relaxed = true),
	)

	private companion object {
		const val SYNC_ID = "test-sync-id"
	}
}
