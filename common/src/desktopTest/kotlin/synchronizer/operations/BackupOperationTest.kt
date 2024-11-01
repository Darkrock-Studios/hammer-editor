package synchronizer.operations

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.CollateIdsState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.BackupOperation
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupOperationTest : BaseTest() {

	@MockK(relaxed = true)
	private lateinit var globalSettingsRepository: GlobalSettingsRepository

	@MockK(relaxed = true)
	private lateinit var backupRepository: ProjectBackupRepository

	private lateinit var strRes: TestStrRes

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		strRes = TestStrRes()
	}

	private fun createOperation(projectDef: ProjectDef): BackupOperation {
		return BackupOperation(
			projectDef = projectDef,
			strRes = strRes,
			globalSettingsRepository = globalSettingsRepository,
			backupRepository = backupRepository,
		)
	}

	@Test
	fun `Golden Path`() = runTest {
		coEvery { globalSettingsRepository.globalSettings.automaticBackups } returns true
		coEvery { backupRepository.supportsBackup() } returns true
		coEvery { backupRepository.createBackup(any()) } returns mockk(relaxed = true)

		val op = createOperation(getProjectDef(PROJECT_2_NAME))

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
		assertIs<CollateIdsState>(data)

		coVerify(exactly = 1) { backupRepository.createBackup(getProjectDef(PROJECT_2_NAME)) }
	}

	@Test
	fun `Backups disabled`() = runTest {
		coEvery { globalSettingsRepository.globalSettings.automaticBackups } returns false
		coEvery { backupRepository.supportsBackup() } returns true
		coEvery { backupRepository.createBackup(any()) } returns mockk(relaxed = true)

		val op = createOperation(getProjectDef(PROJECT_2_NAME))

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
		assertIs<CollateIdsState>(data)

		coVerify(exactly = 0) { backupRepository.createBackup(getProjectDef(PROJECT_2_NAME)) }
	}

	@Test
	fun `Platform does not support backups`() = runTest {
		coEvery { globalSettingsRepository.globalSettings.automaticBackups } returns true
		coEvery { backupRepository.supportsBackup() } returns false
		coEvery { backupRepository.createBackup(any()) } returns mockk(relaxed = true)

		val op = createOperation(getProjectDef(PROJECT_2_NAME))

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
		assertIs<CollateIdsState>(data)

		coVerify(exactly = 0) { backupRepository.createBackup(getProjectDef(PROJECT_2_NAME)) }
	}
}