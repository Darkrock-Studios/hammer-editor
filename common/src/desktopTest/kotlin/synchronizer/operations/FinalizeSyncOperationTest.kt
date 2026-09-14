package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.FinalizeSyncOperation
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
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
import utils.TestClock
import utils.TestStrRes
import utils.sharedFlow
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class FinalizeSyncOperationTest : BaseTest() {

	private lateinit var mockSynchronizers: MockSynchronizers

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var syncDataDatasource: SyncDataDatasource

	@MockK(relaxed = true)
	private lateinit var projectDataDatasource: ProjectDataDatasource

	@MockK(relaxed = true)
	private lateinit var idAllocator: IdAllocator

	private lateinit var strRes: TestStrRes

	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		strRes = TestStrRes()
		mockSynchronizers = MockSynchronizers(false)
		clock = TestClock(Clock.System)
	}

	private fun configureKoin(projectDef: ProjectDef) {
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }

				addSynchronizers(mockSynchronizers)
			}
		})
	}

	private fun createOperation(projectDef: ProjectDef): FinalizeSyncOperation {
		configureKoin(projectDef)
		return FinalizeSyncOperation(
			projectDef = projectDef,
			serverProjectApi = serverProjectApi,
			strRes = strRes,
			entitySynchronizers = EntitySynchronizers(projectDef),
			clock = clock,
			globalSettingsStore = globalSettingsStore,
			syncDataDatasource = syncDataDatasource,
			projectDataDatasource = projectDataDatasource,
			idAllocator = idAllocator,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		val op = createOperation(getProjectDef(PROJECT_2_NAME))

		coEvery { globalSettingsStore.serverSettingsUpdates } returns sharedFlow {
			emit(
				mockk<ServerSettings>().apply {
					every { userId } returns 1L
				}
			)
		}
		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coEvery { synchronizer.finalizeSync() } just Runs
			coEvery { synchronizer.hashEntities(any()) } returns emptySet()
		}
		coEvery { projectDataDatasource.load() } returns StoredProjectData()
		// Per-entity synced hashes were written to disk during transfer; finalize must reload
		// them and prune the deleted ids (7, 8) rather than clobber them with its snapshot.
		coEvery { syncDataDatasource.loadSyncData() } returns projectData.copy(
			syncedHashes = mapOf(1 to "h1", 7 to "h7", 8 to "h8", 10 to "h10")
		)
		coEvery {
			serverProjectApi.endProjectSync(any(), any(), any(), any(), any())
		} returns Result.success("ok")

		val onProgress = mockk<suspend (Float, SyncLogMessage?) -> Unit>(relaxed = true)
		val onLog = mockk<OnSyncLog>(relaxed = true)
		val onConflict = mockk<EntityConflictHandler<ApiProjectEntity>>(relaxed = true)
		val onComplete = mockk<suspend (Boolean) -> Unit>(relaxed = true)

		val initialState = EntityTransferState(
			onlyNew = false,
			clientSyncData = projectData,
			entityState = entityState,
			serverProjectId = projId,
			serverSyncData = beganResponse,
			collatedIds = collatedIds,
			maxId = 12,
			newClientIds = listOf(12),
			allSuccess = true,
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
		assertIs<EntityTransferState>(data)

		mockSynchronizers.synchronizers.forEach { synchronizer ->
			coVerify { synchronizer.finalizeSync() }
		}
		coVerify { serverProjectApi.endProjectSync(1L, projId, beganResponse.syncId, 12, clock.now()) }
		// Downloaded ids may sit above the allocator's sync-start snapshot; it must re-derive.
		coVerify { idAllocator.findNextId() }
		coVerify { onComplete(true) }

		val saved = slot<ProjectSynchronizationData>()
		coVerify { syncDataDatasource.saveSyncData(capture(saved)) }
		saved.captured.apply {
			assertNull(currentSyncId)
			assertEquals(12, lastId)
			assertEquals(clock.now(), lastSync)
			assertEquals(emptyList(), newIds)
			assertEquals(emptyList(), dirty)
			assertEquals(setOf(7, 8, 9), deletedIds)
			// Reloaded from disk with the deleted ids (7, 8) pruned; 1 and 10 survive.
			assertEquals(mapOf(1 to "h1", 10 to "h10"), syncedHashes)
			assertEquals(
				ProjectContentHasher.hash(emptySet(), ProjectDataHasher.hash(ProjectData())),
				cachedProjectHash
			)
			assertEquals(ProjectContentHasher.ALGO_VERSION, hashAlgoVersion)
		}
	}
}