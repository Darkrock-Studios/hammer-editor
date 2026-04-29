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
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.project.ProjectDataSaveResult
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectNotFound
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ServerProjectDataRepositoryTest : BaseTest() {

	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var repository: ServerProjectDataRepository

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project", ProjectId("Test UUID"))
	private val unknownProjectDef = ProjectDefinition("Other Project", ProjectId("Unknown UUID"))

	private val sampleData = ProjectData(
		authorName = "Pat",
		theme = ProjectTheme("#FF000000", "#FFFFFFFF"),
		wordCountGoal = WordCountGoal(WordCountGoal.Cadence.DAY, 500),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()

		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		seedAccountAndProject()

		repository = ServerProjectDataRepository(
			projectDataDao = ProjectDataDao(testDatabase),
			projectDao = ProjectDao(testDatabase),
			json = createJsonSerializer(),
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
		val result = repository.save(userId, projectDef, sampleData, originalHash = null)
		assertTrue(isSuccess(result))
		val saved = assertIs<ProjectDataSaveResult.Saved>(result.data)
		assertEquals(sampleData, saved.dto.data)
		assertEquals(ProjectDataHasher.hash(sampleData), saved.dto.hash)
	}

	@Test
	fun `save then load roundtrips`() = runTest {
		repository.save(userId, projectDef, sampleData, originalHash = null)

		val loaded = repository.load(userId, projectDef)
		assertTrue(isSuccess(loaded))
		val dto = loaded.data!!
		assertEquals(sampleData, dto.data)
		assertEquals(ProjectDataHasher.hash(sampleData), dto.hash)
	}

	@Test
	fun `save with matching originalHash overwrites`() = runTest {
		val first = repository.save(userId, projectDef, sampleData, originalHash = null)
		assertTrue(isSuccess(first))
		val firstHash = (first.data as ProjectDataSaveResult.Saved).dto.hash

		val updated = sampleData.copy(authorName = "Sam")
		val second = repository.save(userId, projectDef, updated, originalHash = firstHash)
		assertTrue(isSuccess(second))
		val saved = assertIs<ProjectDataSaveResult.Saved>(second.data)
		assertEquals(updated, saved.dto.data)
	}

	@Test
	fun `save with mismatched originalHash returns Conflict`() = runTest {
		repository.save(userId, projectDef, sampleData, originalHash = null)

		val updated = sampleData.copy(authorName = "Sam")
		val result = repository.save(
			userId,
			projectDef,
			updated,
			originalHash = "wrong-hash",
		)
		assertTrue(isSuccess(result))
		val conflict = assertIs<ProjectDataSaveResult.Conflict>(result.data)
		assertEquals(sampleData, conflict.conflict.server)
		assertEquals(ProjectDataHasher.hash(sampleData), conflict.conflict.serverHash)
	}

	@Test
	fun `save with null originalHash on existing row returns Conflict`() = runTest {
		repository.save(userId, projectDef, sampleData, originalHash = null)

		val result = repository.save(
			userId,
			projectDef,
			sampleData.copy(authorName = "Sam"),
			originalHash = null,
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
		val result = repository.save(userId, unknownProjectDef, sampleData, originalHash = null)
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
			lastSync = "2026-04-28 09:00:00",
			lastId = 0,
		)
	}
}
