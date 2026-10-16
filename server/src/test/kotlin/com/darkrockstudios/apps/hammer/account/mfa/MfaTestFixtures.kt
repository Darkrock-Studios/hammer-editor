package com.darkrockstudios.apps.hammer.account.mfa

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AccountRecoveryCodeDao
import com.darkrockstudios.apps.hammer.database.AccountTotpDao
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.database.MfaLoginChallengeDao
import com.darkrockstudios.apps.hammer.encryption.AesGcmContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.SimpleFileBasedAesGcmKeyProvider
import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A real [MfaRepository] over [database], with a settable clock. */
class MfaTestFixture(database: Database, start: Instant = Clock.System.now()) {
	var now: Instant = start
	val clock = object : Clock {
		override fun now() = this@MfaTestFixture.now
	}

	private val random = SecureRandom()
	private val base64 = Base64.Default
	private val fileSystem = FakeFileSystem()
	private val codec = KeyringCodec(random, base64)
	private val keyringJson = codec.serialize(
		Keyring(
			content = RoleKeys("v1", mapOf("v1" to "content-key")),
			tokenHmac = RoleKeys("v1", mapOf("v1" to "token-key")),
		)
	)
	private val keyringManager = KeyringManager(
		object : ServerSecretProvider {
			override fun loadKeyring() = keyringJson
		},
		codec, fileSystem, "/none".toPath(),
	)

	val tokenHasher = TokenHasher(keyringManager, ServerSecretManager(fileSystem, random), base64)
	val encryptor = AesGcmContentEncryptor("content-key", "v1", SimpleFileBasedAesGcmKeyProvider(base64), random)
	val accountDao = AccountDao(database)
	val totpDao = AccountTotpDao(database)
	val challengeDao = MfaLoginChallengeDao(database)

	val repository = MfaRepository(
		accountDao = accountDao,
		totpDao = totpDao,
		recoveryCodeDao = AccountRecoveryCodeDao(database),
		challengeDao = challengeDao,
		encryptor = encryptor,
		encryptorRegistry = ContentEncryptorRegistry(listOf(encryptor, PlaintextContentEncryptor())),
		tokenHasher = tokenHasher,
		serverConfig = ServerConfig(host = "write.example.com"),
		random = random,
		base64 = base64,
		clock = clock,
	)

	suspend fun newAccount(email: String = "mfa-${Random.nextLong()}@example.com"): Long =
		accountDao.createAccount(email, "hash", Base64.encode(Random.nextBytes(32)), isAdmin = false)

	fun codeAt(secret: String, at: Instant = now): String =
		Totp.code(Totp.base32Decode(secret), Totp.stepAt(at.epochSeconds))

	/** Enrolls TOTP on [userId] and steps the clock past the confirm code. */
	suspend fun enroll(userId: Long): Enrolled {
		val enrollment = checkNotNull(repository.beginTotp(userId))
		val codes = checkNotNull(repository.confirmTotp(userId, codeAt(enrollment.secret)))
		now += Totp.STEP_SECONDS.seconds
		return Enrolled(enrollment.secret, codes)
	}

	data class Enrolled(val secret: String, val recoveryCodes: List<String>)
}
