package com.darkrockstudios.apps.hammer.project.datasource

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.DeletedEntityDao
import com.darkrockstudios.apps.hammer.database.DeletedProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.StoryEntityDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDatabaseDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectDatabaseDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var testDatabase: SqliteTestDatabase
	private lateinit var json: Json
	private lateinit var clock: TestClock
	private lateinit var contentEncryptor: ContentEncryptor
	private lateinit var cipherSecretGenerator: SecureTokenGenerator
	private lateinit var base64: Base64

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project", ProjectId("Test UUID"))

	@BeforeEach
	override fun setup() {
		super.setup()
		json = createJsonSerializer()
		clock = TestClock(Clock.System)
		fileSystem = FakeFileSystem()
		base64 = createTokenBase64()
		val secureRandom = SecureRandom()
		contentEncryptor = AesGcmContentEncryptor(
			SimpleFileBasedAesGcmKeyProvider(
				fileSystem,
				base64,
				secureRandom
			), secureRandom
		)
		cipherSecretGenerator = SecureTokenGenerator(AccountsRepository.CIPHER_SALT_LENGTH, base64)

		testDatabase = SqliteTestDatabase()
		testDatabase.initialize()

		setupKoin()
	}

	private fun createDatasource(): ProjectDatabaseDatasource {
		return ProjectDatabaseDatasource(
			accountDao = AccountDao(testDatabase),
			projectDao = ProjectDao(testDatabase, clock),
			deletedProjectDao = DeletedProjectDao(testDatabase),
			storyEntityDao = StoryEntityDao(testDatabase),
			deletedEntityDao = DeletedEntityDao(testDatabase),
			encryptor = contentEncryptor,
			json = json,
		)
	}

	@Test
	fun `Check Project Exists`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()
		val result = datasource.checkProjectExists(userId, projectDef)
		assertTrue(result)
	}

	@Test
	fun `Check Project Exists - No Project`() = runTest {
		setupAccount(testDatabase)
		val datasource = createDatasource()
		val result = datasource.checkProjectExists(userId, projectDef)
		assertFalse(result)
	}

	@Test
	fun `Create Project`() = runTest {
		setupAccount(testDatabase)
		val datasource = createDatasource()

		var result = testDatabase.serverDatabase.projectQueries
			.findProjectByName(1, projectDef.name)
			.executeAsOneOrNull()
		assertNull(result)

		datasource.createProject(userId, projectDef.name)

		result = testDatabase.serverDatabase.projectQueries
			.findProjectByName(1, projectDef.name)
			.executeAsOneOrNull()
		assertNotNull(result)
	}

	@Test
	fun `Delete Project`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val result = datasource.deleteProject(userId, projectDef.uuid)
		assertTrue(isSuccess(result))

		val projectExists = testDatabase.serverDatabase.projectQueries
			.hasProjectById(userId, projectDef.uuid.id)
			.executeAsOne()
		assertFalse(projectExists)

		val entities = testDatabase.serverDatabase.storyEntityQueries
			.getAllEntities(userId, 1)
			.executeAsList()
		assertEquals(0, entities.size)
	}

	@Test
	fun `Load Project Sync Data`() = runTest {
		val syncData = ProjectSyncData(
			lastSync = sqliteDateTimeStringToInstant("2023-10-03 17:08:13"),
			lastId = 20,
			deletedIds = setOf(3, 8, 9, 11, 13, 17, 18, 19)
		)

		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val result = datasource.loadProjectSyncData(userId, projectDef)
		assertEquals(syncData, result)
	}

	@Test
	fun `Find Last ID`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val lastId = datasource.findLastId(userId, projectDef)
		assertEquals(20, lastId)
	}

	@Test
	fun `Find Entity Types`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		var entityType = datasource.findEntityType(1, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.SCENE, entityType)

		entityType = datasource.findEntityType(6, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.SCENE_DRAFT, entityType)

		entityType = datasource.findEntityType(10, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.TIMELINE_EVENT, entityType)

		entityType = datasource.findEntityType(14, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY, entityType)

		entityType = datasource.findEntityType(16, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.NOTE, entityType)
	}

	@Test
	fun `Find Entity Defs`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val entityDefs = datasource.getEntityDefs(userId, projectDef) { true }
		val expectedDefs = listOf(
			EntityDefinition(1, ApiProjectEntity.Type.SCENE),
			EntityDefinition(2, ApiProjectEntity.Type.SCENE),
			EntityDefinition(4, ApiProjectEntity.Type.SCENE),
			EntityDefinition(5, ApiProjectEntity.Type.SCENE),
			EntityDefinition(6, ApiProjectEntity.Type.SCENE_DRAFT),
			EntityDefinition(7, ApiProjectEntity.Type.SCENE_DRAFT),
			EntityDefinition(10, ApiProjectEntity.Type.TIMELINE_EVENT),
			EntityDefinition(12, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
			EntityDefinition(14, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
			EntityDefinition(15, ApiProjectEntity.Type.SCENE),
			EntityDefinition(16, ApiProjectEntity.Type.NOTE),
			EntityDefinition(20, ApiProjectEntity.Type.NOTE),
		)

		assertEquals(expectedDefs, entityDefs.sortedBy { it.id })
	}

	@Test
	fun `Load Entity - Decode Scene JSON - SerializationException`() = runTest {
		val userId = 1L
		val entityId = 1

		json = mockk()

		val exception = SerializationException("test")
		every { json.decodeFromString<ApiProjectEntity.SceneEntity>(any(), any()) } answers {
			throw exception
		}

		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val result = datasource.loadEntity(
			userId,
			projectDef,
			entityId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)

		assertTrue(isFailure(result))
		assertEquals(exception, result.exception)
	}

	@Test
	fun `Load Entity - Decode Scene JSON - Entity Not Found`() = runTest {
		val userId = 1L
		val entityId = 22 // Not a real Entity ID

		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val result = datasource.loadEntity(
			userId,
			projectDef,
			entityId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)

		assertTrue(isFailure(result))
		val resultException = result.exception
		assertIs<EntityNotFound>(resultException)
		assertEquals(entityId, resultException.id)
	}

	@Test
	fun `Delete Entity`() = runTest {
		val entityId = 1L
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val result =
			datasource.deleteEntity(
				userId,
				ApiProjectEntity.Type.SCENE,
				projectDef,
				entityId.toInt()
			)
		assertTrue(result.isSuccess)

		val exists = testDatabase.serverDatabase.storyEntityQueries
			.checkExists(userId, 1, entityId)
			.executeAsOne()
		assertFalse(exists)

		val isDeleted = testDatabase.serverDatabase.deletedEntityQueries
			.checkIsDeleted(userId, 1, entityId)
			.executeAsOne()
		assertTrue(isDeleted)
	}

	@Test
	fun `Delete Entity - Failure - Wrong Type`() = runTest {
		val entityId = 1L
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()

		val existsBefore =
			testDatabase.serverDatabase.storyEntityQueries.checkExists(userId, 1, entityId)
				.executeAsOne()
		assertTrue(existsBefore)

		val result =
			datasource.deleteEntity(
				userId,
				ApiProjectEntity.Type.NOTE,
				projectDef,
				entityId.toInt()
			)
		assertTrue(result.isSuccess)

		val existsAfter =
			testDatabase.serverDatabase.storyEntityQueries.checkExists(userId, 1, entityId)
				.executeAsOne()
		assertFalse(existsAfter)
	}

	@Test
	fun `Store Entity - Scene Entity`() = runTest {
		val entityId = 1
		val entity = ApiProjectEntity.SceneEntity(
			id = entityId,
			name = "test",
			content = "test content",
			order = 0,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
			outline = "outline",
			notes = "notes",
		)

		setupAccount(testDatabase)
		val datasource = createDatasource()

		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		val result = datasource.storeEntity(
			userId,
			projectDef,
			entity,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)
		assertTrue(result.isSuccess)

		val exists = testDatabase.serverDatabase.storyEntityQueries
			.checkExists(userId, 1, entityId.toLong())
			.executeAsOne()
		assertTrue(exists)
	}

	@Test
	fun `Get Project`() = runTest {
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()
		val project = datasource.getProject(userId, projectDef.uuid)
		assertEquals(projectDef, project)
	}

	@Test
	fun `Rename Project`() = runTest {
		val newProjectName = "New Project Name"
		setupEntities(testDatabase, contentEncryptor)
		val datasource = createDatasource()
		val success = datasource.renameProject(userId, projectDef.uuid, newProjectName)
		assertTrue(success)
	}

	private fun setupAccount(testDatabase: SqliteTestDatabase) {
		testDatabase.serverDatabase.accountQueries.createAccount(
			email = "test@test.com",
			salt = "salt",
			password_hash = "hash",
			cipher_secret = cipherSecretGenerator.generateToken(),
			is_admin = false,
		)
	}

	private fun setupEntities(testDatabase: SqliteTestDatabase, encryptor: ContentEncryptor) {
		setupAccount(testDatabase)

		testDatabase.serverDatabase.projectQueries.insertProject(
			uuid = projectDef.uuid.id,
			name = projectDef.name,
			userId = 1,
			lastSync = "2023-10-03 17:08:13",
			lastId = 20,
		)

		insertEntity(testDatabase, 1, ApiProjectEntity.Type.SCENE, encryptor)
		insertEntity(testDatabase, 2, ApiProjectEntity.Type.SCENE, encryptor)
		insertDeletedEntity(testDatabase, 3)
		insertEntity(testDatabase, 4, ApiProjectEntity.Type.SCENE, encryptor)
		insertEntity(testDatabase, 5, ApiProjectEntity.Type.SCENE, encryptor)
		insertEntity(testDatabase, 6, ApiProjectEntity.Type.SCENE_DRAFT, encryptor)
		insertEntity(testDatabase, 7, ApiProjectEntity.Type.SCENE_DRAFT, encryptor)
		insertDeletedEntity(testDatabase, 8)
		insertDeletedEntity(testDatabase, 9)
		insertEntity(testDatabase, 10, ApiProjectEntity.Type.TIMELINE_EVENT, encryptor)
		insertDeletedEntity(testDatabase, 11)
		insertEntity(testDatabase, 12, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY, encryptor)
		insertDeletedEntity(testDatabase, 13)
		insertEntity(testDatabase, 14, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY, encryptor)
		insertEntity(testDatabase, 15, ApiProjectEntity.Type.SCENE, encryptor)
		insertEntity(testDatabase, 16, ApiProjectEntity.Type.NOTE, encryptor)
		insertDeletedEntity(testDatabase, 17)
		insertDeletedEntity(testDatabase, 18)
		insertDeletedEntity(testDatabase, 19)
		insertEntity(testDatabase, 20, ApiProjectEntity.Type.NOTE, encryptor)
	}

	private fun insertEntity(
		testDatabase: SqliteTestDatabase,
		id: Long,
		type: ApiProjectEntity.Type,
		encryptor: ContentEncryptor
	) {
		val cipherSecret =
			testDatabase.serverDatabase.accountQueries.getAccount(1).executeAsOne().cipher_secret

		testDatabase.serverDatabase.storyEntityQueries.insertNew(
			userId = 1,
			projectId = 1,
			id = id,
			type = type.toStringId(),
			content = runBlocking { encryptor.encrypt("test-content", cipherSecret) },
			cipher = contentEncryptor.cipherName(),
			hash = "test-hash",
		)
	}

	private fun insertDeletedEntity(testDatabase: SqliteTestDatabase, id: Long) {
		testDatabase.serverDatabase.deletedEntityQueries.markEntityDeleted(
			userId = 1,
			projectId = 1,
			id = id,
		)
	}
}