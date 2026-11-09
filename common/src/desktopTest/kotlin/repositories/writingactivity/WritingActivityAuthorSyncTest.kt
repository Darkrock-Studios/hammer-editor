package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivityResponse
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityTransferState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.WritingActivitySyncOperation
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityRepository
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.server.WritingActivityApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import synchronizer.operations.beganResponse
import synchronizer.operations.collatedIds
import synchronizer.operations.entityState
import synchronizer.operations.projectData
import testProjectId
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The own-slot merge rule against real slot files. */
class WritingActivityAuthorSyncTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var datasource: WritingActivityDatasource
	private lateinit var repository: WritingActivityRepository
	private val globalSettingsStore: GlobalSettingsStore = mockk(relaxed = true)
	private val api: WritingActivityApi = mockk()
	private val statisticsRepository: StatisticsRepository = mockk(relaxed = true)
	private val uploaded = slot<DeviceLog>()

	private val projectDef = ProjectDef(
		id = testProjectId("Test Project"),
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	private val ownDeviceId = "device-self"
	private val me = 7L
	private val other = 9L

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
			}
		})

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		datasource = WritingActivityDatasource(fileSystem, createTomlSerializer(), projectDef)
		repository = WritingActivityRepository(datasource, globalSettingsStore, projectDef)

		coEvery { globalSettingsStore.userIdOrThrow() } returns me
		coEvery { globalSettingsStore.ensureInstallId() } returns ownDeviceId
		every { globalSettingsStore.deviceLabelOrDefault() } returns "Desktop"
		coEvery { api.uploadDeviceLog(any(), any(), any(), capture(uploaded)) } returns Result.success("ok")
	}

	private suspend fun sync(vararg slots: WritingActivitySlot) {
		coEvery { api.getWritingActivity(any(), any()) } returns
			Result.success(WritingActivityResponse(slots.toList()))
		val operation = WritingActivitySyncOperation(
			projectDef = projectDef,
			repository = repository,
			tracker = mockk<WritingSessionTracker>(relaxed = true),
			api = api,
			globalSettingsStore = globalSettingsStore,
			statisticsRepository = statisticsRepository,
		)
		val onProgress: suspend (Float, SyncLogMessage?) -> Unit = { _, _ -> }
		val onLog: OnSyncLog = { }
		val onConflict: EntityConflictHandler<ApiProjectEntity> = mockk(relaxed = true)
		operation.execute(state(), onProgress, onLog, onConflict) { }
	}

	private fun state() = EntityTransferState(
		onlyNew = false,
		clientSyncData = projectData,
		entityState = entityState,
		serverSyncData = beganResponse,
		collatedIds = collatedIds,
		maxId = 1,
		newClientIds = emptyList(),
		allSuccess = true,
	)

	private fun session(hour: Int, words: Int) = WritingSession(
		startedAt = Instant.parse("2026-04-28T${hour.toString().padStart(2, '0')}:00:00Z"),
		endedAt = Instant.parse("2026-04-28T${hour.toString().padStart(2, '0')}:30:00Z"),
		wordsWritten = words,
	)

	@Test
	fun `the own slot takes the author the server stamped, and keeps it through later saves`() = runTest {
		repository.saveOwnLog(listOf(session(9, 10)))

		sync(WritingActivitySlot(ownDeviceId, DeviceLog("Desktop", listOf(session(9, 10)), me, "My Pen")))
		repository.saveOwnLog(listOf(session(9, 10), session(11, 5)))

		val own = repository.loadOwnLog()
		assertEquals(me, own.authorId)
		assertEquals("My Pen", own.authorName)
		assertEquals(2, own.sessions.size)
	}

	@Test
	fun `another account's slot for this device is kept apart, never merged into the own file`() = runTest {
		repository.saveOwnLog(listOf(session(9, 10)))
		val theirs = DeviceLog("Desktop", listOf(session(14, 300)), other, "Their Pen")

		sync(
			WritingActivitySlot(ownDeviceId, DeviceLog("Desktop", listOf(session(9, 10)), me, "My Pen")),
			WritingActivitySlot(ownDeviceId, theirs),
		)

		assertEquals(listOf(session(9, 10)), repository.loadOwnLog().sessions)
		assertEquals(listOf(session(9, 10)), uploaded.captured.sessions)
		val slots = repository.loadAllLogs()
		assertEquals(2, slots.size)
		assertEquals(theirs, slots.single { it.log.authorId == other }.log)
	}

	@Test
	fun `a slot with no author for this device is the own slot`() = runTest {
		sync(WritingActivitySlot(ownDeviceId, DeviceLog("Desktop", listOf(session(9, 10)))))

		assertEquals(listOf(session(9, 10)), repository.loadOwnLog().sessions)
		assertNull(repository.loadOwnLog().authorId)
	}

	@Test
	fun `another device's pre-author file is replaced by its authored copies`() = runTest {
		datasource.saveDeviceLog("device-x", DeviceLog("Phone", listOf(session(8, 40))))

		sync(
			WritingActivitySlot("device-x", DeviceLog("Phone", listOf(session(8, 40)), me, "My Pen")),
			WritingActivitySlot("device-x", DeviceLog("Phone", listOf(session(12, 60)), other, "Their Pen")),
		)

		assertFalse(fileSystem.exists(datasource.getDeviceLogPath("device-x")))
		val remote = repository.loadAllLogs().filter { it.deviceId == "device-x" }
		assertEquals(setOf(me, other), remote.map { it.log.authorId }.toSet())
	}

	@Test
	fun `an own file stamped with another account becomes its slot, not uploaded as ours`() = runTest {
		datasource.saveDeviceLog(ownDeviceId, DeviceLog("Desktop", listOf(session(9, 10)), other, "Their Pen"))
		datasource.saveSlot(WritingActivitySlot(ownDeviceId, DeviceLog("Desktop", listOf(session(8, 5)), other, "Their Pen")))

		sync()

		assertTrue(repository.loadOwnLog().sessions.isEmpty())
		assertTrue(uploaded.captured.sessions.isEmpty())
		val theirs = repository.loadAllLogs().single { it.log.authorId == other }
		assertEquals(listOf(session(9, 10)), theirs.log.sessions)
		verify(exactly = 1) { statisticsRepository.markDirty() }
	}

	@Test
	fun `an account signing back in takes its slot from the server and drops its synced copy`() = runTest {
		datasource.saveDeviceLog(ownDeviceId, DeviceLog("Desktop", listOf(session(9, 10)), other, "Their Pen"))
		val theirServerCopy = DeviceLog("Desktop", listOf(session(8, 5)), other, "Their Pen")

		sync(WritingActivitySlot(ownDeviceId, theirServerCopy))
		coEvery { globalSettingsStore.userIdOrThrow() } returns other
		sync(WritingActivitySlot(ownDeviceId, theirServerCopy))

		assertEquals(listOf(session(8, 5)), repository.loadOwnLog().sessions)
		assertEquals(listOf(session(8, 5)), uploaded.captured.sessions)
		assertEquals(1, repository.loadAllLogs().size)
	}

	@Test
	fun `stats are marked stale only when a sync changes the slots`() = runTest {
		val theirs = WritingActivitySlot("device-x", DeviceLog("Phone", listOf(session(8, 40)), other, "Their Pen"))

		sync(theirs)
		verify(exactly = 1) { statisticsRepository.markDirty() }

		sync(theirs)
		verify(exactly = 1) { statisticsRepository.markDirty() }
	}
}
