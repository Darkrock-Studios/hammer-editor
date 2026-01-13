package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MAX_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MIN_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthToken
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.*
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class AccountsRepositoryTest : BaseTest() {

	private lateinit var accountDao: AccountDao
	private lateinit var authTokenDao: AuthTokenDao
	private lateinit var clock: TestClock
	private lateinit var tokenHasher: TokenHasher
	private val b64: Base64 = createTokenBase64()

	private val tokenGenerator = SecureTokenGenerator(Token.LENGTH, b64)

	private val userId = 1L
	private val email = "test@example.com"
	private val installId = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"
	private val password = "power123"
	private val bearerToken = tokenGenerator.generateToken()
	private val refreshToken = tokenGenerator.generateToken()
	private val cipherSecret = SecureTokenGenerator(16, b64).generateToken()
	private lateinit var secureRandom: SecureRandom

	private lateinit var account: Account

	private fun createAuthToken() = AuthToken(
		user_id = userId,
		install_id = installId,
		token = "$bearerToken-hashed",  // Tokens are now stored hashed
		refresh = "$refreshToken-hashed",  // Tokens are now stored hashed
		created = (Clock.System.now() - 365.days).toISO8601(),
		expires = (Clock.System.now() + 30.days).toISO8601()
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		accountDao = mockk()
		authTokenDao = mockk()
		tokenHasher = mockk()

		secureRandom = SecureRandom()

		// Mock tokenHasher to return a predictable hash (just add "-hashed" suffix for testing)
		coEvery { tokenHasher.hashToken(any()) } answers {
			val token = firstArg<String>()
			"$token-hashed"
		}

		setupKoin()

		clock = TestClock(Clock.System)

		// Generate a fresh Argon2 hash for the password
		val hashedPassword = AccountsRepository.hashPassword(password = password)

		account = Account(
			id = userId,
			email = email,
			password_hash = hashedPassword,
			cipher_secret = cipherSecret,
			created = (Clock.System.now() - 128.days).toSqliteDateTimeString(),
			is_admin = true,
			last_sync = Clock.System.now().toSqliteDateTimeString(),
			pen_name = null,
			bio = null,
			email_verified = true,
			community_member = false,
		)
	}

	@Test
	fun `Login - Success`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account

		// Mock setToken since login now creates fresh tokens
		coEvery { authTokenDao.setToken(any(), any(), any(), any()) } just Runs

		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result =
			accountsRepository.login(email = email, installId = installId, password = password)
		assertTrue(result.isSuccess)
	}

	@Test
	fun `Login - Wrong password`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.login(
			email = email,
			installId = installId,
			password = password + "4"
		)
		assertTrue(result.isFailure)
	}

	@Test
	fun `Login - No User`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.login(
			email = "no@account.com",
			installId = installId,
			password = "power1234"
		)
		assertTrue(result.isFailure)
	}

	@Test
	fun `Create Account - Success`() = runTest {
		coEvery { accountDao.numAccounts() } returns 1
		coEvery { accountDao.findAccount(any()) } returns null
		coEvery { accountDao.createAccount(any(), any(), any(), any()) } returns userId
		coEvery { authTokenDao.setToken(any(), any(), any(), any()) } just Runs
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = password
		)
		assertTrue(isSuccess(result))

		val token = result.data
		assertTrue(token.isValid(), "Token should be valid")
	}

	@Test
	fun `Create Account - Failure - Existing Account`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = password
		)
		assertTrue(result.isFailure)
	}

	@Test
	fun `Create Account - Failure - Password Short`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = "x".repeat(MIN_PASSWORD_LENGTH - 1)
		)
		assertTrue(isFailure(result))
		assertEquals(
			AccountsRepository.Companion.PasswordResult.TOO_SHORT,
			(result.exception as InvalidPassword).result
		)
	}

	@Test
	fun `Create Account - Failure - Password Long`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = "x".repeat(MAX_PASSWORD_LENGTH + 1)
		)
		assertTrue(isFailure(result))
		assertTrue(result.exception is InvalidPassword)
		assertEquals(
			AccountsRepository.Companion.PasswordResult.TOO_LONG,
			(result.exception as InvalidPassword).result
		)
	}

	@Test
	fun `Create Account - Failure - Invalid Email`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = "notanemail",
			installId = installId,
			password = password
		)
		assertTrue(isFailure(result))
		assertTrue(result.exception is CreateFailed)
		assertEquals("account already exists", result.error)
	}

	@Test
	fun `Check Token - Success`() = runTest {
		val token = createAuthToken()
		coEvery { authTokenDao.getTokenByAuthToken(any()) } returns token
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.checkToken(userId, bearerToken)
		assertTrue(isSuccess(result))
		assertEquals(userId, result.data)
	}

	@Test
	fun `Check Token - Failure`() = runTest {
		coEvery { authTokenDao.getTokenByAuthToken(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.checkToken(userId, bearerToken)
		assertTrue(result.isFailure)
	}

	@Test
	fun `hashPassword generates valid Argon2 encoded string`() {
		val hash = AccountsRepository.hashPassword("testPassword123")

		// Verify format starts with Argon2 identifier (i, d, or id variant)
		assertTrue(hash.startsWith("\$argon2"), "Hash should start with \$argon2")
	}

	@Test
	fun `hashPassword generates different salts each time`() {
		val testPassword = "testPassword123"
		val hash1 = AccountsRepository.hashPassword(testPassword)
		val hash2 = AccountsRepository.hashPassword(testPassword)

		// Same password should generate different hashes (due to random salt)
		assertTrue(hash1 != hash2, "Same password should generate different hashes")
	}

	@Test
	fun `Login fails with old SHA-256 hash`() = runTest {
		// Create account with old-style SHA-256 hash (hex string)
		val oldHash = "abc123def456789"
		val accountWithOldHash = account.copy(password_hash = oldHash)

		coEvery { accountDao.findAccount(any()) } returns accountWithOldHash
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.login(
			email = email,
			installId = installId,
			password = password
		)

		// Should fail because old hash cannot be verified
		assertTrue(result.isFailure, "Login should fail with old SHA-256 hash")
	}
}