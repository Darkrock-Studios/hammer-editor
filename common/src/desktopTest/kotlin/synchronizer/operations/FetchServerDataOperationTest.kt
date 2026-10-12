package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FetchServerDataOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDeletedElsewhereException
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import getProjectDef
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
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
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectsRepository: ProjectsRepository

	@MockK
	private lateinit var projectCreateResolver: ProjectCreateResolver

	@MockK
	private lateinit var syncJournal: SyncJournal

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
			globalSettingsStore = globalSettingsStore,
			projectCreateResolver = projectCreateResolver,
			projectsRepository = projectsRepository,
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
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

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
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

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

	@Test
	fun `A generic failure does not recreate the project`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		stubSettingsAndMetadata()
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

		stubSettingsAndMetadata()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returnsMany
			listOf(Result.failure(gone()), Result.success(beganResponse))
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns
			ServerCreateOutcome.Created(projId, alreadyExisted = false, reminted = false)
		coEvery { syncJournal.loadSyncData() } returns clearedJournal

		val result = execute(op)

		assertTrue(isSuccess(result))
		val data = assertIs<FetchServerDataState>(result.data)
		// The old baseline described a server copy that no longer exists.
		coVerify { projectsRepository.clearSyncBaseline(projectDef) }
		assertEquals(clearedJournal, data.clientSyncData)
		assertEquals(projId, data.serverProjectId)
		coVerify(exactly = 2) { serverProjectApi.beginProjectSync(1L, projId, entityState, false) }
	}

	@Test
	fun `A 410 retry uses the id the server answered the create with`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)
		val adoptedId = ProjectId("adopted-id")

		stubSettingsAndMetadata()
		coEvery { serverProjectApi.beginProjectSync(any(), projId, any(), any()) } returns Result.failure(gone())
		coEvery { serverProjectApi.beginProjectSync(any(), adoptedId, any(), any()) } returns
			Result.success(beganResponse)
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns
			ServerCreateOutcome.Created(adoptedId, alreadyExisted = true, reminted = false)
		coEvery { syncJournal.loadSyncData() } returns projectData

		val result = execute(op)

		assertTrue(isSuccess(result))
		assertEquals(adoptedId, assertIs<FetchServerDataState>(result.data).serverProjectId)
	}

	@Test
	fun `A 410 for a project deleted elsewhere fails without retrying`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		stubSettingsAndMetadata()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns ServerCreateOutcome.DeletedElsewhere

		val result = execute(op)

		assertFalse(isSuccess(result))
		assertIs<ProjectDeletedElsewhereException>(result.exception)
		coVerify(exactly = 1) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
		coVerify(exactly = 0) { projectsRepository.clearSyncBaseline(any()) }
	}

	@Test
	fun `A second 410 fails rather than looping`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val op = createOperation(projectDef)

		stubSettingsAndMetadata()
		coEvery { serverProjectApi.beginProjectSync(any(), any(), any(), any()) } returns Result.failure(gone())
		coEvery { projectCreateResolver.createInOwnSession(projectDef) } returns
			ServerCreateOutcome.Created(projId, alreadyExisted = false, reminted = false)
		coEvery { syncJournal.loadSyncData() } returns projectData

		val result = execute(op)

		assertFalse(isSuccess(result))
		coVerify(exactly = 1) { projectCreateResolver.createInOwnSession(any()) }
		coVerify(exactly = 2) { serverProjectApi.beginProjectSync(any(), any(), any(), any()) }
	}

	private fun gone() = HttpFailureException(
		statusCode = HttpStatusCode.Gone,
		error = HttpResponseError(error = "Project Not Found", displayMessage = "Project does not exist"),
	)

	private suspend fun stubSettingsAndMetadata() {
		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		coEvery { projectMetadataDatasource.loadMetadata(any()) } returns metadata
	}

	private suspend fun execute(op: FetchServerDataOperation) = op.execute(
		state = FetchLocalDataState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
		),
		onProgress = mockk(relaxed = true),
		onLog = mockk(relaxed = true),
		onConflict = mockk(relaxed = true),
		onComplete = mockk(relaxed = true),
	)
}