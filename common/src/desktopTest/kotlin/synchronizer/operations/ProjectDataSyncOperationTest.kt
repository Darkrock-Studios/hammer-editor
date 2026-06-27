package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictUnresolvedException
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDataSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import createProjectDirectories
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Classical-style: drives a real [ProjectDataRepository] / [ProjectDataDatasource] over a
 * [FakeFileSystem] and a real [ProjectDataConflictBroker]. Only the network ([ProjectDataApi])
 * and the global settings store are mocked. Disk-backed [StoredProjectData] is the observable
 * outcome.
 */
class ProjectDataSyncOperationTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	@MockK
	private lateinit var api: ProjectDataApi

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var syncDataDatasource: SyncDataDatasource

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: ProjectDataDatasource
	private lateinit var repository: ProjectDataRepository
	private lateinit var broker: ProjectDataConflictBroker

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		coEvery { globalSettingsStore.userIdOrThrow() } returns 1L

		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProjectDirectories(fileSystem)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncDataDatasource }
			}
		})

		datasource = ProjectDataDatasource(fileSystem, toml, projectDef)
		repository = ProjectDataRepository(datasource, projectDef)
		broker = ProjectDataConflictBroker(projectDef)
	}

	private fun createOperation() = ProjectDataSyncOperation(
		projectDef = projectDef,
		repository = repository,
		api = api,
		broker = broker,
		globalSettingsStore = globalSettingsStore,
	)

	private fun state() = IdConflictResolutionState(
		onlyNew = false,
		clientSyncData = projectData,
		entityState = entityState,
		serverProjectId = projId,
		serverSyncData = beganResponse,
		collatedIds = collatedIds,
		maxId = 11,
		newClientIds = emptyList(),
	)

	private suspend fun ProjectDataSyncOperation.run() = execute(
		state = state(),
		onProgress = { _, _ -> },
		onLog = {},
		onConflict = {},
		onComplete = {},
	)

	@Test
	fun `server has no data and local is empty is a no-op success`() = runTest {
		coEvery { api.getProjectData(any(), any()) } returns Result.success(null)

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		coVerify(exactly = 0) { api.uploadProjectData(any(), any(), any(), any()) }
	}

	@Test
	fun `server has no data but local has data uploads with no original hash`() = runTest {
		val local = ProjectData(authorName = "Author")
		datasource.save(StoredProjectData(local, lastSyncedHash = null))
		coEvery { api.getProjectData(any(), any()) } returns Result.success(null)
		coEvery {
			api.uploadProjectData(any(), any(), local, null)
		} returns Result.success(ProjectDataDto(local, "uploaded-hash"))

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals("uploaded-hash", repository.state.value?.lastSyncedHash)
		coVerify(exactly = 1) { api.uploadProjectData(any(), any(), local, null) }
	}

	@Test
	fun `server hash matching local records the synced hash when it was missing`() = runTest {
		val local = ProjectData(authorName = "Author")
		val hash = ProjectDataHasher.hash(local)
		datasource.save(StoredProjectData(local, lastSyncedHash = null))
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(local, hash))

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals(hash, repository.state.value?.lastSyncedHash)
		coVerify(exactly = 0) { api.uploadProjectData(any(), any(), any(), any()) }
	}

	@Test
	fun `server hash matching local already recorded is a pure no-op`() = runTest {
		val local = ProjectData(authorName = "Author")
		val hash = ProjectDataHasher.hash(local)
		datasource.save(StoredProjectData(local, lastSyncedHash = hash))
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(local, hash))

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals(StoredProjectData(local, hash), repository.state.value)
	}

	@Test
	fun `local unchanged since last sync fast-forwards to the server copy`() = runTest {
		val local = ProjectData(authorName = "Author")
		val localHash = ProjectDataHasher.hash(local)
		datasource.save(StoredProjectData(local, lastSyncedHash = localHash))
		val server = ProjectData(authorName = "Server Author")
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(server, "server-hash"))

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals(StoredProjectData(server, "server-hash"), repository.state.value)
	}

	@Test
	fun `local diverged from server uploads with the last synced hash as baseline`() = runTest {
		val local = ProjectData(authorName = "Local Edit")
		datasource.save(StoredProjectData(local, lastSyncedHash = "stale-hash"))
		val server = ProjectData(authorName = "Server")
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(server, "server-hash"))
		coEvery {
			api.uploadProjectData(any(), any(), local, "stale-hash")
		} returns Result.success(ProjectDataDto(local, "new-hash"))

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals("new-hash", repository.state.value?.lastSyncedHash)
		coVerify(exactly = 1) { api.uploadProjectData(any(), any(), local, "stale-hash") }
	}

	@Test
	fun `a non-conflict upload failure fails the sync`() = runTest {
		val local = ProjectData(authorName = "Local Edit")
		datasource.save(StoredProjectData(local, lastSyncedHash = "stale-hash"))
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(ProjectData(authorName = "Server"), "server-hash"))
		coEvery {
			api.uploadProjectData(any(), any(), any(), any())
		} returns Result.failure(RuntimeException("upload exploded"))

		val result = createOperation().run()

		assertTrue(isFailure(result))
	}

	@Test
	fun `a failure to load remote data fails the sync`() = runTest {
		coEvery { api.getProjectData(any(), any()) } returns
			Result.failure(RuntimeException("network down"))

		val result = createOperation().run()

		assertTrue(isFailure(result))
	}

	@Test
	fun `a resolved conflict re-uploads against the server hash and succeeds`() = runTest {
		val local = ProjectData(authorName = "Local Edit")
		datasource.save(StoredProjectData(local, lastSyncedHash = "stale-hash"))
		val server = ProjectData(authorName = "Server")
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(server, "server-hash"))
		coEvery {
			api.uploadProjectData(any(), any(), local, "stale-hash")
		} returns Result.failure(
			ProjectDataConflictException(ProjectDataConflictDto(server, "server-hash"))
		)
		val resolved = ProjectData(authorName = "Merged")
		coEvery {
			api.uploadProjectData(any(), any(), resolved, "server-hash")
		} returns Result.success(ProjectDataDto(resolved, "resolved-hash"))

		val watcher = launch {
			broker.conflicts.receive()
			broker.resolve(resolved)
		}

		val result = createOperation().run()

		assertTrue(isSuccess(result))
		assertEquals(StoredProjectData(resolved, "resolved-hash"), repository.state.value)
		coVerify(exactly = 1) { api.uploadProjectData(any(), any(), resolved, "server-hash") }
		watcher.cancel()
	}

	@Test
	fun `a failed conflict-resolution upload fails the sync`() = runTest {
		val local = ProjectData(authorName = "Local Edit")
		datasource.save(StoredProjectData(local, lastSyncedHash = "stale-hash"))
		val server = ProjectData(authorName = "Server")
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(server, "server-hash"))
		coEvery {
			api.uploadProjectData(any(), any(), local, "stale-hash")
		} returns Result.failure(
			ProjectDataConflictException(ProjectDataConflictDto(server, "server-hash"))
		)
		val resolved = ProjectData(authorName = "Merged")
		coEvery {
			api.uploadProjectData(any(), any(), resolved, "server-hash")
		} returns Result.failure(RuntimeException("resolution rejected"))

		val watcher = launch {
			broker.conflicts.receive()
			broker.resolve(resolved)
		}

		val result = createOperation().run()

		assertTrue(isFailure(result))
		watcher.cancel()
	}

	@Test
	fun `an aborted conflict fails instead of hanging`() = runTest {
		val local = ProjectData(authorName = "Local Edit")
		datasource.save(StoredProjectData(local, lastSyncedHash = "stale-hash"))
		val server = ProjectData(authorName = "Server")
		coEvery { api.getProjectData(any(), any()) } returns
			Result.success(ProjectDataDto(server, "server-hash"))
		coEvery {
			api.uploadProjectData(any(), any(), any(), any())
		} returns Result.failure(
			ProjectDataConflictException(ProjectDataConflictDto(server, "server-hash"))
		)

		// Bulk-sync flow has no resolver: abort the conflict so the sync fails rather than hangs.
		val watcher = launch {
			broker.conflicts.receive()
			broker.abort()
		}

		assertFailsWith<ProjectDataConflictUnresolvedException> {
			createOperation().execute(
				state = state(),
				onProgress = { _: Float, _: SyncLogMessage? -> },
				onLog = {},
				onConflict = {},
				onComplete = {},
			)
		}

		watcher.cancel()
	}
}
