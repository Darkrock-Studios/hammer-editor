package com.darkrockstudios.apps.hammer.e2e.util

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.datamigrator.migrations.getSerializerForType
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import com.darkrockstudios.apps.hammer.utilities.toISO8601
import korlibs.io.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class TestAccount(
	val email: String,
	val password: String,
	val salt: String,
	val isAdmin: Boolean = false,
) {
	val passwordHash: String = AccountsRepository.hashPassword(password, salt)
}

class TestProject(
	val name: String,
	val uuid: UUID,
	val userId: Long,
)

object E2eTestData {
	val json = createJsonSerializer()
	val b64 = createTokenBase64()
	val cipherSecretGenerator = SecureTokenGenerator(AccountsRepository.CIPHER_SALT_LENGTH, b64)

	fun createAccount(account: TestAccount, database: SqliteTestDatabase) {
		database.serverDatabase.accountQueries.createAccount(
			email = account.email,
			salt = account.salt,
			password_hash = account.passwordHash,
			cipher_secret = cipherSecretGenerator.generateToken(),
			is_admin = account.isAdmin,
		)
	}

	fun createProject(
		project: TestProject,
		database: SqliteTestDatabase,
	) {
		database.serverDatabase.projectQueries.createProject(
			userId = project.userId,
			name = project.name,
			uuid = project.uuid.toString(),
		)
	}

	val preDeletedProject1 = UUID.randomUUID()
	fun addDeletedProject(
		userId: Long,
		uuid: UUID,
		database: SqliteTestDatabase
	) {
		database.serverDatabase.deletedProjectQueries.addDeletedProject(
			userId = userId,
			uuid = uuid.toString(),
		)
	}

	val tokenGenerator = SecureTokenGenerator(Token.LENGTH, b64)
	fun createAuthToken(
		userId: Long,
		installId: String,
		expires: Instant = Clock.System.now() + 30.days,
		database: SqliteTestDatabase,
	): Token {
		val newToken = Token(
			userId = userId,
			auth = tokenGenerator.generateToken(),
			refresh = tokenGenerator.generateToken(),
		)

		database.serverDatabase.authTokenQueries.setToken(
			userId = userId,
			token = newToken.auth,
			refresh = newToken.refresh,
			expires = expires.toISO8601(),
			installId = installId,
		)

		return newToken
	}

	fun insertEntity(
		userId: Long,
		projectId: Long,
		entity: ApiProjectEntity,
		testDatabase: SqliteTestDatabase,
		contentEncryptor: ContentEncryptor,
	) {
		val cipherSecret = testDatabase.serverDatabase.accountQueries
			.getAccount(userId).executeAsOne().cipher_secret

		val entityJson = json.encodeToString(getSerializerForType(entity.type), entity)
		val encryptedJson = runBlocking {
			contentEncryptor.encrypt(entityJson, cipherSecret)
		}

		testDatabase.serverDatabase.storyEntityQueries.insertNew(
			userId = userId,
			projectId = projectId,
			id = entity.id.toLong(),
			type = entity.type.toStringId(),
			content = encryptedJson,
			cipher = contentEncryptor.cipherName(),
			hash = EntityHasher.hashEntity(entity),
		)
	}

	fun insertDeletedEntity(
		id: Long,
		userId: Long,
		projectId: Long,
		testDatabase: SqliteTestDatabase,
	) {
		testDatabase.serverDatabase.deletedEntityQueries.markEntityDeleted(
			userId = userId,
			projectId = projectId,
			id = id,
		)
	}

	fun createTestScene(id: Int): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = id,
			name = "test scene $id",
			content = "test content $id",
			order = id - 1,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
		)
	}

	fun createTestNote(id: Int): ApiProjectEntity.NoteEntity {
		return ApiProjectEntity.NoteEntity(
			id = id,
			content = "test content $id",
			created = Instant.fromEpochSeconds(id * 1000000L),
		)
	}
}