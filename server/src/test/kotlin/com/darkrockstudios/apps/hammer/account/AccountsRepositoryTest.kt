package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthToken
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.*
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
		created = (Clock.System.now() - 365.days),
		expires = (Clock.System.now() + 30.days)
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
			created = (Clock.System.now() - 128.days),
			is_admin = true,
			last_sync = Clock.System.now(),
			pen_name = null,
			bio = null,
			email_verified = true,
			community_member = false,
			deleted_at = null,
		)
	}

	@Test
	fun `Login - Success`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account

		val storedToken = slot<Token>()
		coEvery { authTokenDao.setToken(userId, installId, capture(storedToken), any()) } just Runs

		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result =
			accountsRepository.login(email = email, installId = installId, password = password)
		assertTrue(isSuccess(result))
		assertEquals(userId, result.data.userId)
		assertTrue(result.data.isValid())
		// The client gets plaintext tokens; only their hashes may be persisted.
		assertEquals("${result.data.auth}-hashed", storedToken.captured.auth)
		assertEquals("${result.data.refresh}-hashed", storedToken.captured.refresh)
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
	fun `Login - soft-deleted account with correct password gets the pending-deletion failure`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account.copy(deleted_at = Clock.System.now())
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result =
			accountsRepository.login(email = email, installId = installId, password = password)

		assertTrue(result.isFailure)
		result as ServerResult.Failure
		assertEquals("Account pending deletion", result.error)
		// A locked-out account must never be issued a token.
		coVerify(exactly = 0) { authTokenDao.setToken(any(), any(), any(), any()) }
	}

	@Test
	fun `Login - soft-deleted account with wrong password gets the generic failure`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account.copy(deleted_at = Clock.System.now())
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.login(
			email = email,
			installId = installId,
			password = password + "wrong"
		)

		assertTrue(result.isFailure)
		result as ServerResult.Failure
		// The account's deletion state must not leak to a password guesser.
		assertEquals("Invalid credentials", result.error)
	}

	@Test
	fun `CreateAccount - soft-deleted existing account gets the pending-deletion failure`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account.copy(deleted_at = Clock.System.now())
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(email, installId, password)

		assertTrue(result.isFailure)
		result as ServerResult.Failure
		assertEquals("account pending deletion", result.error)
	}

	@Test
	fun `Login - unknown account and wrong password are indistinguishable`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account
		coEvery { accountDao.findAccount("no@account.com") } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val wrongPassword = accountsRepository.login(email, password + "x", installId)
		val unknownAccount = accountsRepository.login("no@account.com", password, installId)

		assertTrue(isFailure(wrongPassword))
		assertTrue(isFailure(unknownAccount))
		assertEquals(
			(wrongPassword.displayMessage as? ServerMessage.Resource)?.r?.joinToString("|"),
			(unknownAccount.displayMessage as? ServerMessage.Resource)?.r?.joinToString("|"),
			"Login must not reveal whether the account exists",
		)
	}

	@Test
	fun `Create Account - Success`() = runTest {
		coEvery { accountDao.numAccounts() } returns 1
		// Not the first account on the server, so it must not be created as admin.
		coEvery { accountDao.findAccount(any()) } returns null
		coEvery { accountDao.createAccount(any(), any(), any(), isAdmin = false) } returns userId
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
		assertEquals(userId, token.userId)
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
			password = "x".repeat(PasswordValidator.MIN_LENGTH - 1)
		)
		assertTrue(isFailure(result))
		assertEquals(
			PasswordValidationResult.TOO_SHORT,
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
			password = "x".repeat(PasswordValidator.MAX_LENGTH + 1)
		)
		assertTrue(isFailure(result))
		assertTrue(result.exception is InvalidPassword)
		assertEquals(
			PasswordValidationResult.TOO_LONG,
			(result.exception as InvalidPassword).result
		)
	}

	@Test
	fun `Create Account - Failure - Invalid Email`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.createAccount(
			email = "notanemail",
			installId = installId,
			password = password
		)
		assertTrue(isFailure(result))
		assertTrue(result.exception is CreateFailed)
		assertEquals("invalid email", result.error)
	}

	@Test
	fun `Check Token - Success`() = runTest {
		val token = createAuthToken()
		// Argument-specific: the lookup must use the hashed token, never the plaintext.
		coEvery { authTokenDao.getTokenByAuthToken("$bearerToken-hashed") } returns token
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
	fun `Refresh Token - Success`() = runTest {
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns createAuthToken()
		coEvery { authTokenDao.setToken(any(), any(), any(), any()) } just Runs
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.refreshToken(userId, installId, refreshToken)

		assertTrue(isSuccess(result))
		assertTrue(result.data.isValid())
	}

	@Test
	fun `Refresh Token - No existing token`() = runTest {
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.refreshToken(userId, installId, refreshToken)

		assertTrue(result.isFailure)
	}

	@Test
	fun `Refresh Token - Mismatched refresh token`() = runTest {
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns
			createAuthToken().copy(refresh = "some-other-hashed")
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.refreshToken(userId, installId, refreshToken)

		assertTrue(result.isFailure)
	}

	@Test
	fun `Refresh Token - succeeds within the refresh window after the access token expired`() = runTest {
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns
			createAuthToken().copy(expires = clock.now() - 30.days)
		coEvery { authTokenDao.setToken(any(), any(), any(), any()) } just Runs
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.refreshToken(userId, installId, refreshToken)

		assertTrue(isSuccess(result))
	}

	@Test
	fun `Refresh Token - fails once expired beyond the refresh window`() = runTest {
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns
			createAuthToken().copy(expires = clock.now() - 200.days)
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		val result = accountsRepository.refreshToken(userId, installId, refreshToken)

		assertTrue(result.isFailure)
	}

	@Test
	fun `purgeExpiredTokens deletes only tokens past the refresh window`() = runTest {
		val now = clock.now()
		coEvery { authTokenDao.deleteExpiredBefore(any()) } just Runs
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, tokenHasher, b64)

		accountsRepository.purgeExpiredTokens(now)

		coVerify { authTokenDao.deleteExpiredBefore(now - AccountsRepository.REFRESH_TOKEN_WINDOW) }
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