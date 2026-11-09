package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingActivitySlot
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.WritingActivityDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.ServerWritingActivityRepository
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.ServerResult
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

	private lateinit var testDatabase: SharedPostgresTestDatabase
	private lateinit var repository: ServerWritingActivityRepository

	private val userId = 1L
	private val coAuthorId = 2L
	private val owner = OwnerId.unchecked(userId)
	private val projectDef = ProjectDefinition("Test Project", ProjectId("11111111-1111-1111-1111-111111111111"))
	private val unknownProjectDef = ProjectDefinition("Other Project", ProjectId("22222222-2222-2222-2222-222222222222"))

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		testDatabase = SharedPostgresTestDatabase()
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
		val result = repository.loadAll(owner, projectDef)
		assertTrue(isSuccess(result))
		assertTrue(result.data.slots.isEmpty())
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

		val save = save("device-a", log)
		assertTrue(isSuccess(save))

		val load = repository.loadAll(owner, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(listOf(WritingActivitySlot("device-a", log.stamped())), load.data.slots)
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
		save("device-a", initial)

		val updated = initial.copy(
			sessions = initial.sessions + WritingSession(
				startedAt = Instant.parse("2026-04-28T15:00:00Z"),
				endedAt = Instant.parse("2026-04-28T16:00:00Z"),
				wordsWritten = 80,
			)
		)
		save("device-a", updated)

		val load = repository.loadAll(owner, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(listOf(WritingActivitySlot("device-a", updated.stamped())), load.data.slots)
	}

	@Test
	fun `loadAll returns each device under its own key`() = runTest {
		val deviceA = DeviceLog(deviceLabel = "Desktop", sessions = emptyList())
		val deviceB = DeviceLog(deviceLabel = "Phone", sessions = emptyList())
		save("device-a", deviceA)
		save("device-b", deviceB)

		val load = repository.loadAll(owner, projectDef)
		assertTrue(isSuccess(load))
		assertEquals(setOf("device-a", "device-b"), load.data.slots.map { it.deviceId }.toSet())
	}

	@Test
	fun `the stored author replaces whatever the client sent`() = runTest {
		val sent = DeviceLog(deviceLabel = "Desktop", authorId = 99, authorName = "Forged")
		save("device-a", sent, authorId = coAuthorId, authorName = "Co Pen")

		val slot = (repository.loadAll(owner, projectDef) as ServerResult.Success).data.slots.single()
		assertEquals(coAuthorId, slot.log.authorId)
		assertEquals("Co Pen", slot.log.authorName)
	}

	@Test
	fun `two authors on one install keep separate slots`() = runTest {
		val ownerLog = DeviceLog(deviceLabel = "Desktop", sessions = listOf(session(100)))
		val coAuthorLog = DeviceLog(deviceLabel = "Desktop", sessions = listOf(session(7)))
		save("device-a", ownerLog)
		save("device-a", coAuthorLog, authorId = coAuthorId, authorName = "Co Pen")

		val slots = (repository.loadAll(owner, projectDef) as ServerResult.Success).data.slots
		assertEquals(
			setOf(
				WritingActivitySlot("device-a", ownerLog.stamped()),
				WritingActivitySlot("device-a", coAuthorLog.copy(authorId = coAuthorId, authorName = "Co Pen")),
			),
			slots.toSet(),
		)
	}

	@Test
	fun `a later upload records the author's current pen name`() = runTest {
		save("device-a", DeviceLog(deviceLabel = "Desktop"), authorName = "Old Pen")
		save("device-a", DeviceLog(deviceLabel = "Desktop"), authorName = "New Pen")

		val slot = (repository.loadAll(owner, projectDef) as ServerResult.Success).data.slots.single()
		assertEquals("New Pen", slot.log.authorName)
	}

	@Test
	fun `loadAll fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.loadAll(owner, unknownProjectDef)
		assertTrue(!isSuccess(result))
		assertTrue(result.exception is ProjectNotFound)
	}

	@Test
	fun `saveDeviceLog fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.saveDeviceLog(
			owner = owner,
			projectDef = unknownProjectDef,
			authorId = userId,
			authorName = OWNER_PEN,
			deviceId = "device-a",
			log = DeviceLog(deviceLabel = "Desktop"),
		)
		assertTrue(!isSuccess(result))
		assertTrue(result.exception is ProjectNotFound)
	}

	private suspend fun save(
		deviceId: String,
		log: DeviceLog,
		authorId: Long = userId,
		authorName: String? = OWNER_PEN,
	) = repository.saveDeviceLog(owner, projectDef, authorId, authorName, deviceId, log)

	private fun DeviceLog.stamped() = copy(authorId = userId, authorName = OWNER_PEN)

	private fun session(words: Int) = WritingSession(
		startedAt = Instant.parse("2026-04-28T09:00:00Z"),
		endedAt = Instant.parse("2026-04-28T10:00:00Z"),
		wordsWritten = words,
	)

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

	private companion object {
		const val OWNER_PEN = "Owner Pen"
	}
}
