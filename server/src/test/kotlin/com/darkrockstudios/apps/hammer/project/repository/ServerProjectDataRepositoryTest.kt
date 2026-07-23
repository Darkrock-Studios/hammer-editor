package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDataDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.project.ProjectDataSaveResult
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ServerProjectDataRepositoryTest : BaseTest() {

	private lateinit var testDatabase: SharedPostgresTestDatabase
	private lateinit var repository: ServerProjectDataRepository

	private val json = createJsonSerializer()

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project", ProjectId("11111111-1111-1111-1111-111111111111"))
	private val unknownProjectDef = ProjectDefinition("Other Project", ProjectId("22222222-2222-2222-2222-222222222222"))

	private val sampleData = ProjectData(
		authorName = "Pat",
		theme = ProjectTheme("#FF000000", "#FFFFFFFF"),
		wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
	)
	private val sampleJson: JsonElement
		get() = json.encodeToJsonElement(
			ProjectData.serializer(),
			sampleData
		)
	private val sampleHash: String get() = ProjectDataHasher.hash(sampleData)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		testDatabase = SharedPostgresTestDatabase()
		testDatabase.initialize()

		seedAccountAndProject()

		repository = ServerProjectDataRepository(
			projectDataDao = ProjectDataDao(testDatabase),
			projectDao = ProjectDao(testDatabase),
			json = json,
			clock = Clock.System,
			log = KtorSimpleLogger("ServerProjectDataRepositoryTest"),
		)
	}

	@Test
	fun `load returns null when no row exists`() = runTest {
		val result = repository.load(userId, projectDef)
		assertTrue(isSuccess(result))
		assertNull(result.data)
	}

	@Test
	fun `save with null originalHash succeeds when no existing row`() = runTest {
		val result = repository.save(
			userId,
			projectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
		)
		assertTrue(isSuccess(result))
		val saved = assertIs<ProjectDataSaveResult.Saved>(result.data)
		assertEquals(sampleJson, saved.dto.data)
		assertEquals(sampleHash, saved.dto.hash)
	}

	@Test
	fun `save then load roundtrips`() = runTest {
		repository.save(
			userId,
			projectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
		)

		val loaded = repository.load(userId, projectDef)
		assertTrue(isSuccess(loaded))
		val dto = loaded.data!!
		assertEquals(sampleJson, dto.data)
		assertEquals(sampleHash, dto.hash)
	}

	@Test
	fun `save preserves unknown fields verbatim`() = runTest {
		// A payload from a client newer than this server: extra field the server has never heard of.
		val futuristic =
			JsonObject(sampleJson.jsonObject + ("someFutureField" to JsonPrimitive("kept")))
		val futureHash = "future-client-hash"

		val saved = repository.save(
			userId,
			projectDef,
			futuristic,
			originalHash = null,
			clientHash = futureHash
		)
		assertTrue(isSuccess(saved))
		assertEquals(futuristic, (saved.data as ProjectDataSaveResult.Saved).dto.data)

		val loaded = repository.load(userId, projectDef)
		assertTrue(isSuccess(loaded))
		assertEquals(
			futuristic,
			loaded.data!!.data,
			"unknown fields must survive the round-trip untouched"
		)
		assertEquals(
			futureHash,
			loaded.data!!.hash,
			"the client-supplied hash must be stored verbatim"
		)
	}

	@Test
	fun `save rejects a payload that does not decode as ProjectData`() = runTest {
		// Well-formed JSON, wrong shape: tags must be an array.
		val wrongShape = JsonObject(mapOf("tags" to JsonPrimitive(5)))

		val result = repository.save(
			userId,
			projectDef,
			wrongShape,
			originalHash = null,
			clientHash = "some-hash"
		)

		assertTrue(
			!isSuccess(result),
			"structurally invalid project data must be rejected, not stored"
		)
		val loaded = repository.load(userId, projectDef)
		assertTrue(isSuccess(loaded))
		assertNull(loaded.data, "the rejected payload must not have been persisted")
	}

	@Test
	fun `load treats a wrong-shape row as missing so a fresh upload can heal it`() = runTest {
		// Bypass save()'s validation to simulate a poisoned row (e.g. written by other means).
		val projectId = testDatabase.serverDatabase.projectQueries
			.getProjectId(userId = userId, uuid = projectDef.uuid.id).executeAsOne()
		ProjectDataDao(testDatabase).upsert(
			userId = userId,
			projectId = projectId,
			content = """{"tags": 5}""",
			hash = "poisoned-hash",
			updatedAt = Instant.parse("2026-04-28T09:00:00Z"),
		)

		val loaded = repository.load(userId, projectDef)

		assertTrue(isSuccess(loaded))
		assertNull(
			loaded.data,
			"an undecodable row must be treated as missing, not served to clients"
		)
	}

	@Test
	fun `save without clientHash falls back to server-side hash for legacy clients`() = runTest {
		val result =
			repository.save(userId, projectDef, sampleJson, originalHash = null, clientHash = null)
		assertTrue(isSuccess(result))
		val saved = assertIs<ProjectDataSaveResult.Saved>(result.data)
		assertEquals(sampleHash, saved.dto.hash)
	}

	@Test
	fun `save with matching originalHash overwrites`() = runTest {
		val first = repository.save(
			userId,
			projectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
		)
		assertTrue(isSuccess(first))
		val firstHash = (first.data as ProjectDataSaveResult.Saved).dto.hash

		val updated = sampleData.copy(authorName = "Sam")
		val updatedJson = json.encodeToJsonElement(ProjectData.serializer(), updated)
		val second = repository.save(
			userId, projectDef, updatedJson,
			originalHash = firstHash,
			clientHash = ProjectDataHasher.hash(updated),
		)
		assertTrue(isSuccess(second))
		val saved = assertIs<ProjectDataSaveResult.Saved>(second.data)
		assertEquals(updatedJson, saved.dto.data)
	}

	@Test
	fun `save with mismatched originalHash returns Conflict`() = runTest {
		repository.save(
			userId,
			projectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
		)

		val updated = sampleData.copy(authorName = "Sam")
		val result = repository.save(
			userId, projectDef,
			json.encodeToJsonElement(ProjectData.serializer(), updated),
			originalHash = "wrong-hash",
			clientHash = ProjectDataHasher.hash(updated),
		)
		assertTrue(isSuccess(result))
		val conflict = assertIs<ProjectDataSaveResult.Conflict>(result.data)
		assertEquals(sampleJson, conflict.conflict.server)
		assertEquals(sampleHash, conflict.conflict.serverHash)
	}

	@Test
	fun `save with null originalHash on existing row returns Conflict`() = runTest {
		repository.save(
			userId,
			projectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
		)

		val updated = sampleData.copy(authorName = "Sam")
		val result = repository.save(
			userId, projectDef,
			json.encodeToJsonElement(ProjectData.serializer(), updated),
			originalHash = null,
			clientHash = ProjectDataHasher.hash(updated),
		)
		assertTrue(isSuccess(result))
		assertIs<ProjectDataSaveResult.Conflict>(result.data)
	}

	@Test
	fun `load fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.load(userId, unknownProjectDef)
		assertTrue(!isSuccess(result))
		assertTrue(result.exception is ProjectNotFound)
	}

	@Test
	fun `save fails ProjectNotFound for unknown project`() = runTest {
		val result = repository.save(
			userId,
			unknownProjectDef,
			sampleJson,
			originalHash = null,
			clientHash = sampleHash
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
