package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ServerWritingActivityRepositoryTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var repository: ServerWritingActivityRepository

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project", ProjectId("11111111-1111-1111-1111-111111111111"))
	private val unknownProjectDef = ProjectDefinition("Other Project", ProjectId("22222222-2222-2222-2222-222222222222"))

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		seedAccountAndProject()

		repository = ServerWritingActivityRepository(
			writingActivityDao = WritingActivityDao(testDatabase),
			projectDao = ProjectDao(testDatabase),
			json = createJsonSerializer(),
			log = KtorSimpleLogger("ServerWritingActivityRepositoryTest"),
		)
	}

	@Test
	fun `loadAll returns empty when no activity yet`() = runTest {
		val result = repository.loadAll(userId, projectDef)
		assertTrue(isSuccess(result))
		assertTrue(result.data.devices.isEmpty())
	}

	@Test
	fun `save then load roundtrips a device log`() = runTest {
		val log = DeviceLog(
			deviceLabel = "Adam's Desktop",
			sessions = listOf(
				WritingSession(
					startedAt = Instant.parse("2026-04-28T09:00:00Z"),
					endedAt = Instant.parse("2026-04-28T11:30:00Z"),
					wordsWritten = 1247,
					sealed = true,
				)
			),
		)

		val save = repository.saveDeviceLog(userId, projectDef, "device-a", log)
		assertTrue(isSuccess(save))

		val load = repository.loadAll(userId, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(mapOf("device-a" to log), load.data.devices)
	}

	@Test
	fun `save replaces existing slot for same device`() = runTest {
		val initial = DeviceLog(deviceLabel = "Desktop", sessions = listOf(
			WritingSession(
				startedAt = Instant.parse("2026-04-28T09:00:00Z"),
				endedAt = Instant.parse("2026-04-28T10:00:00Z"),
				wordsWritten = 50,
			)
		))
		repository.saveDeviceLog(userId, projectDef, "device-a", initial)

		val updated = initial.copy(
			sessions = initial.sessions + WritingSession(
				startedAt = Instant.parse("2026-04-28T15:00:00Z"),
				endedAt = Instant.parse("2026-04-28T16:00:00Z"),
				wordsWritten = 80,
			)
		)
		repository.saveDeviceLog(userId, projectDef, "device-a", updated)

		val load = repository.loadAll(userId, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(mapOf("device-a" to updated), load.data.devices)
	}

	@Test
	fun `loadAll returns each device under its own key`() = runTest {
		val deviceA = DeviceLog(deviceLabel = "Desktop", sessions = emptyList())
		val deviceB = DeviceLog(deviceLabel = "Phone", sessions = emptyList())
		repository.saveDeviceLog(userId, projectDef, "device-a", deviceA)
		repository.saveDeviceLog(userId, projectDef, "device-b", deviceB)

		val load = repository.loadAll(userId, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(setOf("device-a", "device-b"), load.data.devices.keys)
	}

	@Test
	fun `loadAll fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.loadAll(userId, unknownProjectDef)
		assertTrue(!isSuccess(result))
		assertTrue(result.exception is ProjectNotFound)
	}

	@Test
	fun `saveDeviceLog fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.saveDeviceLog(
			userId,
			unknownProjectDef,
			"device-a",
			DeviceLog(deviceLabel = "Desktop"),
		)
		assertTrue(!isSuccess(result))
		assertTrue(result.exception is ProjectNotFound)
	}

	private fun seedAccountAndProject() {
		val base64 = createTokenBase64()
		val cipherSecretGenerator =
			SecureTokenGenerator(AccountsRepository.CIPHER_SALT_LENGTH, base64)
		testDatabase.serverDatabase.accountQueries.createAccount(
			email = "test@test.com",
			password_hash = "hash",
			cipher_secret = cipherSecretGenerator.generateToken(),
			is_admin = false,
		)
		testDatabase.serverDatabase.projectQueries.insertProject(
			uuid = projectDef.uuid.id,
			name = projectDef.name,
			userId = userId,
			lastSync = Instant.parse("2026-04-28T09:00:00Z"),
			lastId = 0,
		)
	}
}
