package repositories.writingactivity

import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WritingActivityDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: WritingActivityDatasource

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())
		toml = createTomlSerializer()

		datasource = WritingActivityDatasource(
			fileSystem = fileSystem,
			toml = toml,
			projectDef = projectDef,
		)
	}

	@Test
	fun `loadDeviceLog returns null when no log exists yet`() = runTest {
		val log = datasource.loadDeviceLog("device-a")
		assertNull(log)
	}

	@Test
	fun `loadAllDeviceLogs returns empty when folder is missing`() = runTest {
		val logs = datasource.loadAllDeviceLogs()
		assertTrue(logs.isEmpty())
	}

	@Test
	fun `save then load roundtrips the device log`() = runTest {
		val log = DeviceLog(
			deviceLabel = "Adam's Desktop",
			sessions = listOf(
				WritingSession(
					startedAt = Instant.parse("2026-04-28T09:00:00Z"),
					endedAt = Instant.parse("2026-04-28T11:30:00Z"),
					wordsWritten = 1247,
					sealed = true,
				),
				WritingSession(
					startedAt = Instant.parse("2026-04-28T15:00:00Z"),
					endedAt = Instant.parse("2026-04-28T16:00:00Z"),
					wordsWritten = 312,
				),
			),
		)
		datasource.saveDeviceLog("device-a", log)

		val loaded = datasource.loadDeviceLog("device-a")
		assertEquals(log, loaded)
	}

	@Test
	fun `loadAllDeviceLogs reads every per-device file in the folder`() = runTest {
		datasource.saveDeviceLog(
			deviceId = "device-a",
			log = DeviceLog(
				deviceLabel = "Desktop",
				sessions = listOf(
					WritingSession(
						startedAt = Instant.parse("2026-04-28T09:00:00Z"),
						endedAt = Instant.parse("2026-04-28T10:00:00Z"),
						wordsWritten = 100,
					)
				),
			),
		)
		datasource.saveDeviceLog(
			deviceId = "device-b",
			log = DeviceLog(
				deviceLabel = "Phone",
				sessions = listOf(
					WritingSession(
						startedAt = Instant.parse("2026-04-28T18:00:00Z"),
						endedAt = Instant.parse("2026-04-28T18:30:00Z"),
						wordsWritten = 50,
					)
				),
			),
		)

		val all = datasource.loadAllDeviceLogs()
		assertEquals(setOf("device-a", "device-b"), all.keys)
		assertEquals(100, all.getValue("device-a").sessions.single().wordsWritten)
		assertEquals(50, all.getValue("device-b").sessions.single().wordsWritten)
	}

	@Test
	fun `directory layout matches plan`() {
		val dir = datasource.getDirectory()
		assertEquals("/projects/Test Project/scenes/.activity", dir.toString().replace('\\', '/'))
		val deviceFile = datasource.getDeviceLogPath("abc-123")
		assertEquals(
			"/projects/Test Project/scenes/.activity/abc-123.toml",
			deviceFile.toString().replace('\\', '/'),
		)
	}
}
