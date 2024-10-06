package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MAX_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MIN_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthToken
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utilities.toISO8601
import com.darkrockstudios.apps.hammer.utilities.toSqliteDateTimeString
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class AccountsRepositoryTest : BaseTest() {

	private lateinit var accountDao: AccountDao
	private lateinit var authTokenDao: AuthTokenDao
	private lateinit var clock: TestClock
	private val b64: Base64 = createTokenBase64()

	private val tokenGenerator = SecureTokenGenerator(Token.LENGTH, b64)

	private val userId = 1L
	private val email = "test@example.com"
	private val installId = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"
	private val password = "power123"
	private val salt = "12345"
	private val bearerToken = tokenGenerator.generateToken()
	private val refreshToken = tokenGenerator.generateToken()
	private val hashedPassword = AccountsRepository.hashPassword(password = password, salt = salt)
	private val cipherSecret = SecureTokenGenerator(16, b64).generateToken()
	private lateinit var secureRandom: SecureRandom

	private lateinit var account: Account

	private fun createAuthToken() = AuthToken(
		user_id = userId,
		install_id = installId,
		token = bearerToken,
		refresh = refreshToken,
		created = (Clock.System.now() - 365.days).toISO8601(),
		expires = (Clock.System.now() + 30.days).toISO8601()
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		accountDao = mockk()
		authTokenDao = mockk()

		secureRandom = SecureRandom()

		setupKoin()

		clock = TestClock(Clock.System)

		account = Account(
			id = userId,
			email = email,
			salt = salt,
			password_hash = hashedPassword,
			cipher_secret = cipherSecret,
			created = (Clock.System.now() - 128.days).toSqliteDateTimeString(),
			is_admin = true,
			last_sync = Clock.System.now().toSqliteDateTimeString()
		)
	}

	@Test
	fun `Login - Success`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account

		val authToken = createAuthToken()
		coEvery { authTokenDao.getTokenByInstallId(userId, installId) } returns authToken

		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result =
			accountsRepository.login(email = email, installId = installId, password = password)
		assertTrue { result.isSuccess }
	}

	@Test
	fun `Login - Wrong password`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.login(
			email = email,
			installId = installId,
			password = password + "4"
		)
		assertTrue { result.isFailure }
	}

	@Test
	fun `Login - No User`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.login(
			email = "no@account.com",
			installId = installId,
			password = "power1234"
		)
		assertTrue { result.isFailure }
	}

	@Test
	fun `Create Account - Success`() = runTest {
		coEvery { accountDao.numAccounts() } returns 1
		coEvery { accountDao.findAccount(any()) } returns null
		coEvery { accountDao.createAccount(any(), any(), any(), any(), any()) } returns userId
		coEvery { authTokenDao.setToken(any(), any(), any(), any()) } just Runs
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

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
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = password
		)
		assertTrue { result.isFailure }
	}

	@Test
	fun `Create Account - Failure - Password Short`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

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
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.createAccount(
			email = email,
			installId = installId,
			password = "x".repeat(MAX_PASSWORD_LENGTH + 1)
		)
		assertTrue(isFailure(result))
		assertTrue { result.exception is InvalidPassword }
		assertEquals(
			AccountsRepository.Companion.PasswordResult.TOO_LONG,
			(result.exception as InvalidPassword).result
		)
	}

	@Test
	fun `Create Account - Failure - Invalid Email`() = runTest {
		coEvery { accountDao.findAccount(any()) } returns account
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

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
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.checkToken(userId, bearerToken)
		assertTrue(isSuccess(result))
		assertEquals(userId, result.data)
	}

	@Test
	fun `Check Token - Failure`() = runTest {
		coEvery { authTokenDao.getTokenByAuthToken(any()) } returns null
		val accountsRepository =
			AccountsRepository(accountDao, authTokenDao, clock, secureRandom, b64)

		val result = accountsRepository.checkToken(userId, bearerToken)
		assertTrue { result.isFailure }
	}
}