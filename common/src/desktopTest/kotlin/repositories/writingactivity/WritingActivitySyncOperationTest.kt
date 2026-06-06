package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityTransferState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.WritingActivitySyncOperation
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.operations.*
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class WritingActivitySyncOperationTest : BaseTest() {

	@MockK(relaxed = true)
	private lateinit var repository: WritingActivityRepository

	@MockK(relaxed = true)
	private lateinit var tracker: WritingSessionTracker

	@MockK(relaxed = true)
	private lateinit var api: WritingActivityApi

	@MockK(relaxed = true)
	private lateinit var globalSettingsStore: GlobalSettingsStore

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	private val onProgress: suspend (Float, SyncLogMessage?) -> Unit = mockk(relaxed = true)
	private val onLog: OnSyncLog = mockk(relaxed = true)
	private val onConflict: EntityConflictHandler<ApiProjectEntity> = mockk(relaxed = true)
	private val onComplete: suspend () -> Unit = mockk(relaxed = true)

	private val ownDeviceId = "device-self"
	private val deviceLabel = "My Desktop"
	private val userId = 7L

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
			}
		})

		coEvery { globalSettingsStore.userIdOrThrow() } returns userId
		every { globalSettingsStore.deviceLabelOrDefault() } returns deviceLabel
		coEvery { repository.ownDeviceId() } returns ownDeviceId
	}

	private fun createOperation(): WritingActivitySyncOperation = WritingActivitySyncOperation(
		projectDef = projectDef,
		repository = repository,
		tracker = tracker,
		api = api,
		globalSettingsStore = globalSettingsStore,
	)

	private fun startState() = EntityTransferState(
		onlyNew = false,
		clientSyncData = projectData,
		entityState = entityState,
		serverProjectId = projId,
		serverSyncData = beganResponse,
		collatedIds = collatedIds,
		maxId = 1,
		newClientIds = emptyList(),
		allSuccess = true,
	)

	private fun localOnlySession() = WritingSession(
		startedAt = Instant.parse("2026-04-28T10:00:00Z"),
		endedAt = Instant.parse("2026-04-28T10:30:00Z"),
		wordsWritten = 50,
	)

	@Test
	fun `golden path uploads own slot when server has no devices yet`() = runTest {
		coEvery { repository.loadOwnLog() } returns DeviceLog(
			deviceLabel = deviceLabel,
			sessions = listOf(localOnlySession()),
		)
		coEvery { api.getWritingActivity(any(), any(), any()) } returns
			Result.success(WritingActivityResponse(devices = emptyMap()))
		coEvery { api.uploadDeviceLog(any(), any(), any(), any(), any()) } returns
			Result.success("ok")

		val result = createOperation().execute(startState(), onProgress, onLog, onConflict, onComplete)

		assertTrue(isSuccess(result))
		assertEquals(startState(), result.data, "State should pass through unchanged")

		coVerify { repository.saveOwnLog(listOf(localOnlySession())) }
		coVerify {
			api.uploadDeviceLog(
				userId = userId,
				projectName = projectDef.name,
				projectId = projId,
				deviceId = ownDeviceId,
				log = DeviceLog(deviceLabel = deviceLabel, sessions = listOf(localOnlySession())),
			)
		}
		coVerify { tracker.invalidateSessionCache() }
	}

	@Test
	fun `foreign device slots are written wholesale`() = runTest {
		val foreignSession = WritingSession(
			startedAt = Instant.parse("2026-04-28T18:00:00Z"),
			endedAt = Instant.parse("2026-04-28T19:00:00Z"),
			wordsWritten = 200,
		)
		val foreignLog = DeviceLog(deviceLabel = "Phone", sessions = listOf(foreignSession))

		coEvery { repository.loadOwnLog() } returns DeviceLog(deviceLabel, emptyList())
		coEvery { api.getWritingActivity(any(), any(), any()) } returns Result.success(
			WritingActivityResponse(devices = mapOf("device-other" to foreignLog))
		)
		coEvery { api.uploadDeviceLog(any(), any(), any(), any(), any()) } returns Result.success("ok")

		createOperation().execute(startState(), onProgress, onLog, onConflict, onComplete)

		coVerify { repository.replaceForeignDeviceLog("device-other", foreignLog) }
	}

	@Test
	fun `own slot is merged with server version before upload`() = runTest {
		val sharedStartedAt = Instant.parse("2026-04-28T10:00:00Z")
		val localCopy = WritingSession(
			startedAt = sharedStartedAt,
			endedAt = Instant.parse("2026-04-28T10:30:00Z"),
			wordsWritten = 50,
		)
		val serverCopy = WritingSession(
			startedAt = sharedStartedAt,
			endedAt = Instant.parse("2026-04-28T11:00:00Z"),
			wordsWritten = 75,
			sealed = true,
		)

		coEvery { repository.loadOwnLog() } returns DeviceLog(deviceLabel, listOf(localCopy))
		coEvery { api.getWritingActivity(any(), any(), any()) } returns Result.success(
			WritingActivityResponse(devices = mapOf(ownDeviceId to DeviceLog(deviceLabel, listOf(serverCopy))))
		)
		val uploadedLog = slot<DeviceLog>()
		coEvery {
			api.uploadDeviceLog(any(), any(), any(), any(), capture(uploadedLog))
		} returns Result.success("ok")

		createOperation().execute(startState(), onProgress, onLog, onConflict, onComplete)

		val merged = uploadedLog.captured.sessions.single()
		assertEquals(75, merged.wordsWritten)
		assertEquals(Instant.parse("2026-04-28T11:00:00Z"), merged.endedAt)
		assertTrue(merged.sealed)
	}

	@Test
	fun `GET failure skips activity sync without failing the pipeline`() = runTest {
		coEvery { api.getWritingActivity(any(), any(), any()) } returns
			Result.failure(RuntimeException("server unavailable"))

		val result = createOperation().execute(startState(), onProgress, onLog, onConflict, onComplete)

		assertTrue(isSuccess(result), "GET failure must not fail the surrounding sync")
		coVerify(exactly = 0) { api.uploadDeviceLog(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { tracker.invalidateSessionCache() }
	}

	@Test
	fun `POST failure logs but does not fail the pipeline`() = runTest {
		coEvery { repository.loadOwnLog() } returns DeviceLog(deviceLabel, emptyList())
		coEvery { api.getWritingActivity(any(), any(), any()) } returns
			Result.success(WritingActivityResponse())
		coEvery { api.uploadDeviceLog(any(), any(), any(), any(), any()) } returns
			Result.failure(RuntimeException("upload boom"))

		val result = createOperation().execute(startState(), onProgress, onLog, onConflict, onComplete)

		assertTrue(isSuccess(result))
		// Local merge still happened — sealed sessions never shrink even if upload fails.
		coVerify { repository.saveOwnLog(any()) }
		coVerify { tracker.invalidateSessionCache() }
	}
}
