package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataDto
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataConflictException
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictUnresolvedException
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.IdConflictResolutionState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.ProjectDataSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ProjectDataApi
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertFailsWith

class ProjectDataSyncOperationTest : BaseTest() {

	@MockK
	private lateinit var repository: ProjectDataRepository

	@MockK
	private lateinit var api: ProjectDataApi

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
			}
		})
	}

	private fun createOperation(
		projectDef: ProjectDef,
		broker: ProjectDataConflictBroker,
	): ProjectDataSyncOperation {
		configureKoin(projectDef)
		return ProjectDataSyncOperation(
			projectDef = projectDef,
			repository = repository,
			api = api,
			broker = broker,
			globalSettingsStore = globalSettingsStore,
		)
	}

	private fun conflictState(projectDef: ProjectDef) = IdConflictResolutionState(
		onlyNew = false,
		clientSyncData = projectData,
		entityState = entityState,
		serverProjectId = projId,
		serverSyncData = beganResponse,
		collatedIds = collatedIds,
		maxId = 11,
		newClientIds = emptyList(),
	)

	@Test
	fun `Data conflict with no resolver fails instead of hanging when aborted`() = runTest {
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val broker = ProjectDataConflictBroker(projectDef)
		val op = createOperation(projectDef, broker)

		coEvery { globalSettingsStore.userIdOrThrow() } returns 1L
		coEvery { repository.load() } returns StoredProjectData(
			data = ProjectData(authorName = "local"),
			lastSyncedHash = "old-hash",
		)
		coEvery { api.getProjectData(any(), any(), any()) } returns Result.success(
			ProjectDataDto(data = ProjectData(authorName = "server"), hash = "server-hash"),
		)
		coEvery { api.uploadProjectData(any(), any(), any(), any(), any()) } returns Result.failure(
			ProjectDataConflictException(
				ProjectDataConflictDto(
					server = ProjectData(authorName = "server"),
					serverHash = "server-hash",
				),
			),
		)

		// Simulate the bulk-sync flow which has no resolver: abort the conflict.
		val watcher = launch {
			for (conflict in broker.conflicts) {
				broker.abort()
			}
		}

		val onLog = mockk<OnSyncLog>(relaxed = true)

		// Aborting unblocks the operation with a failure (it would otherwise hang here forever).
		// ClientProjectSynchronizer.execute() catches this and reports the project as failed.
		assertFailsWith<ProjectDataConflictUnresolvedException> {
			op.execute(
				state = conflictState(projectDef),
				onProgress = { _: Float, _: SyncLogMessage? -> },
				onLog = onLog,
				onConflict = mockk(relaxed = true),
				onComplete = {},
			)
		}

		watcher.cancel()
	}
}
