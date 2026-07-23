package com.darkrockstudios.apps.hammer.project.datasource

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.*
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utilities.*
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectEntityDatabaseDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var testDatabase: SharedPostgresTestDatabase
	private lateinit var json: Json
	private lateinit var clock: TestClock
	private lateinit var contentEncryptor: ContentEncryptor
	private lateinit var plaintextEncryptor: PlaintextContentEncryptor
	private lateinit var encryptorRegistry: ContentEncryptorRegistry
	private lateinit var cipherSecretGenerator: SecureTokenGenerator
	private lateinit var base64: Base64

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project", ProjectId("11111111-1111-1111-1111-111111111111"))

	@BeforeEach
	override fun setup() {
		super.setup()
		json = createJsonSerializer()
		clock = TestClock(Clock.System)
		fileSystem = FakeFileSystem()
		base64 = createTokenBase64()
		val secureRandom = SecureRandom()
		val codec = KeyringCodec(secureRandom, base64)
		val keyringJson = codec.serialize(codec.generate())
		val keyringManager = KeyringManager(
			object : ServerSecretProvider { override fun loadKeyring() = keyringJson },
			codec, fileSystem, "/nonexistent".toPath(),
		)
		contentEncryptor = AesGcmContentEncryptor(
			keyringManager.activeContentKey(),
			keyringManager.activeContentKeyId(),
			SimpleFileBasedAesGcmKeyProvider(base64),
			secureRandom,
		)
		plaintextEncryptor = PlaintextContentEncryptor()
		encryptorRegistry = ContentEncryptorRegistry(listOf(contentEncryptor, plaintextEncryptor))
		cipherSecretGenerator = SecureTokenGenerator(AccountsRepository.CIPHER_SALT_LENGTH, base64)

		testDatabase = SharedPostgresTestDatabase()
		testDatabase.initialize()

		setupKoin()
	}

	private fun createDatasource(
		maxContentLength: Int = ProjectEntityDatabaseDatasource.MAX_ENTITY_CONTENT_LENGTH,
		activeEncryptor: ContentEncryptor = contentEncryptor,
	): ProjectEntityDatabaseDatasource {
		return ProjectEntityDatabaseDatasource(
			accountDao = AccountDao(testDatabase),
			projectDao = ProjectDao(testDatabase),
			deletedProjectDao = DeletedProjectDao(testDatabase),
			storyEntityDao = StoryEntityDao(testDatabase),
			deletedEntityDao = DeletedEntityDao(testDatabase),
			encryptor = activeEncryptor,
			encryptorRegistry = encryptorRegistry,
			json = json,
			maxContentLength = maxContentLength,
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

		val created = datasource.createProject(userId, projectDef.name)

		result = testDatabase.serverDatabase.projectQueries
			.findProjectByName(1, projectDef.name)
			.executeAsOneOrNull()
		assertNotNull(result)
		assertEquals(created.uuid.id, result.uuid)
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

		val row = getRow(entityId.toLong())
		assertEquals(entity.hash(), row.hash)
		assertEquals(entity, loadScene(datasource, entityId))
	}

	@Test
	fun `Store Entity - Too Large - Rejected`() = runTest {
		val entityId = 1
		val entity = ApiProjectEntity.SceneEntity(
			id = entityId,
			name = "test",
			content = "x".repeat(256),
			order = 0,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
			outline = "outline",
			notes = "notes",
		)

		setupAccount(testDatabase)
		// Tiny cap so a normal entity exceeds it without allocating the real 64 MiB.
		val datasource = createDatasource(maxContentLength = 16)

		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		val result = datasource.storeEntity(
			userId,
			projectDef,
			entity,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)

		assertTrue(isFailure(result))
		val exception = result.exception
		assertIs<EntityTooLargeException>(exception)
		assertEquals(entityId, exception.id)
		assertEquals(16, exception.maxSize)

		// Nothing was written.
		val exists = testDatabase.serverDatabase.storyEntityQueries
			.checkExists(userId, 1, entityId.toLong())
			.executeAsOne()
		assertFalse(exists)
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

		val renamed = datasource.getProject(userId, projectDef.uuid)
		assertEquals(newProjectName, renamed?.name)
	}

	@Test
	fun `Load Entity - Mixed Cipher Tags - Each Decrypts With Its Own Encryptor`() = runTest {
		setupAccount(testDatabase)
		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		val aesEntity = insertSceneEntityRaw(1, "aes content", contentEncryptor, contentEncryptor.cipherName())
		val plainEntity = insertSceneEntityRaw(2, "plain content", plaintextEncryptor, plaintextEncryptor.cipherName())
		val nullEntity = insertSceneEntityRaw(3, "null cipher content", plaintextEncryptor, cipherTag = null)

		val datasource = createDatasource()

		assertEquals(aesEntity, loadScene(datasource, 1))
		assertEquals(plainEntity, loadScene(datasource, 2))
		assertEquals(nullEntity, loadScene(datasource, 3))
	}

	@Test
	fun `Load Entity - Unknown Cipher Tag - Loud Failure`() = runTest {
		setupAccount(testDatabase)
		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		insertSceneEntityRaw(1, "content", plaintextEncryptor, cipherTag = "bogus:v9")

		val datasource = createDatasource()

		assertFailsWith<IllegalStateException> {
			datasource.loadEntity(
				userId, projectDef, 1,
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer(),
			)
		}
	}

	@Test
	fun `Load Entity - AES Tag But Non-Ciphertext Content - Clean Failure`() = runTest {
		setupAccount(testDatabase)
		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		// Content stored as plaintext JSON but tagged as AES: decrypting it must fail
		// cleanly (not throw an uncaught crypto/Base64 exception).
		insertSceneEntityRaw(1, "content", plaintextEncryptor, cipherTag = contentEncryptor.cipherName())

		val result = createDatasource().loadEntity(
			userId, projectDef, 1,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer(),
		)

		assertTrue(isFailure(result))
	}

	@Test
	fun `Load Entity - Stale Cached Hash - Repaired On Read`() = runTest {
		setupAccount(testDatabase)
		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		// The hash column no longer matches the stored content (e.g. the hash
		// algorithm changed since the row was written). The content is the truth.
		val entity = insertSceneEntityRaw(
			1, "content", contentEncryptor, contentEncryptor.cipherName(),
			hash = "stale-hash",
		)

		val datasource = createDatasource()

		assertEquals(entity, loadScene(datasource, 1))
		assertEquals(entity.hash(), getRow(1).hash)
	}

	@Test
	fun `Store Entity - Cipher Is Orthogonal To Hash`() = runTest {
		val entity = ApiProjectEntity.SceneEntity(
			id = 1,
			name = "test",
			content = "test content",
			order = 0,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
			outline = "outline",
			notes = "notes",
		)

		setupAccount(testDatabase)
		testDatabase.serverDatabase.projectQueries
			.createProject(userId, projectDef.name, projectDef.uuid.id)

		val aesDatasource = createDatasource(activeEncryptor = contentEncryptor)
		assertTrue(
			aesDatasource.storeEntity(
				userId, projectDef, entity,
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer(),
			).isSuccess
		)
		val afterAes = getRow(1)
		assertEquals(contentEncryptor.cipherName(), afterAes.cipher)

		val plainDatasource = createDatasource(activeEncryptor = plaintextEncryptor)
		assertTrue(
			plainDatasource.storeEntity(
				userId, projectDef, entity,
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer(),
			).isSuccess
		)
		val afterPlain = getRow(1)
		assertEquals(plaintextEncryptor.cipherName(), afterPlain.cipher)

		assertEquals(afterAes.hash, afterPlain.hash)
		assertNotEquals(afterAes.content, afterPlain.content)
	}

	private suspend fun loadScene(
		datasource: ProjectEntityDatabaseDatasource,
		entityId: Int,
	): ApiProjectEntity.SceneEntity {
		val result = datasource.loadEntity(
			userId, projectDef, entityId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer(),
		)
		assertTrue(isSuccess(result))
		return result.data
	}

	private fun getRow(entityId: Long) =
		testDatabase.serverDatabase.storyEntityQueries
			.getEntity(userId, 1, entityId)
			.executeAsOne()

	private suspend fun insertSceneEntityRaw(
		id: Int,
		plaintextContent: String,
		encryptor: ContentEncryptor,
		cipherTag: String?,
		hash: String? = null,
	): ApiProjectEntity.SceneEntity {
		val entity = ApiProjectEntity.SceneEntity(
			id = id,
			name = "scene $id",
			content = plaintextContent,
			order = 0,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
			outline = "",
			notes = "",
		)
		val jsonString = json.encodeToString(ApiProjectEntity.SceneEntity.serializer(), entity)
		val cipherSecret =
			testDatabase.serverDatabase.accountQueries.getAccount(1).executeAsOne().cipher_secret
		val content = encryptor.encrypt(jsonString, cipherSecret)

		testDatabase.serverDatabase.storyEntityQueries.insertNew(
			userId = 1,
			projectId = 1,
			id = id.toLong(),
			type = ApiProjectEntity.Type.SCENE.toStringId(),
			content = content,
			cipher = cipherTag,
			hash = hash ?: entity.hash(),
		)
		return entity
	}

	private fun setupAccount(testDatabase: SharedPostgresTestDatabase) {
		testDatabase.serverDatabase.accountQueries.createAccount(
			email = "test@test.com",
			password_hash = "hash",
			cipher_secret = cipherSecretGenerator.generateToken(),
			is_admin = false,
		)
	}

	private fun setupEntities(testDatabase: SharedPostgresTestDatabase, encryptor: ContentEncryptor) {
		setupAccount(testDatabase)

		testDatabase.serverDatabase.projectQueries.insertProject(
			uuid = projectDef.uuid.id,
			name = projectDef.name,
			userId = 1,
			lastSync = Instant.parse("2023-10-03T17:08:13Z"),
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
		testDatabase: SharedPostgresTestDatabase,
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

	private fun insertDeletedEntity(testDatabase: SharedPostgresTestDatabase, id: Long) {
		testDatabase.serverDatabase.deletedEntityQueries.markEntityDeleted(
			userId = 1,
			projectId = 1,
			id = id,
		)
	}
}