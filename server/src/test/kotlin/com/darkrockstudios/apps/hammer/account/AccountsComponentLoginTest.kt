package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.mfa.MfaRepository
import com.darkrockstudios.apps.hammer.account.mfa.MfaTestFixture
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AccountsComponentLoginTest : BaseTest() {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK
	private lateinit var projectsRepository: ProjectsRepository

	@MockK
	private lateinit var termsOfServiceRepository: TermsOfServiceRepository

	private lateinit var mfa: MfaTestFixture
	private val mfaRepository: MfaRepository get() = mfa.repository

	private val validEmail = "test@test.com"
	private val validPassword = "qweasdZXC123"
	private val installId = "123456789"
	private val token = Token(
		userId = 2,
		auth = "123",
		refresh = "abc"
	)
	private val account = Account(
		id = token.userId,
		email = validEmail,
		password_hash = "asd123s",
		cipher_secret = "abc",
		created = Instant.parse("2024-01-01T11:00:00Z"),
		is_admin = false,
		last_sync = Instant.parse("2024-01-01T12:00:00Z"),
		pen_name = null,
		bio = null,
		email_verified = true,
		community_member = false,
		deleted_at = null,
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()
		val db = SharedPostgresTestDatabase()
		db.initialize()
		mfa = MfaTestFixture(db)
	}

	private fun component() = AccountsComponent(
		accountsRepository,
		whiteListRepository,
		projectsRepository,
		termsOfServiceRepository,
		mfaRepository,
		PluginRegistry(emptyList())
	)

	/** A real account row for MFA state, behind the mocked password check. */
	private suspend fun mfaAccount(): Pair<Account, MfaTestFixture.Enrolled> {
		val email = "login-${Random.nextLong()}@example.com"
		val userId = mfa.newAccount(email)
		val enrolled = mfa.enroll(userId)
		val mfaAccount = account.copy(id = userId, email = email)
		coEvery { whiteListRepository.isOnWhiteList(email) } returns true
		coEvery { accountsRepository.findAccount(email) } returns mfaAccount
		coEvery { accountsRepository.getAccountOrNull(userId) } returns mfaAccount
		coEvery { accountsRepository.verifyPassword(email, validPassword) } returns SResult.success(mfaAccount)
		coEvery { accountsRepository.issueToken(userId, any()) } returns token.copy(userId = userId)
		return mfaAccount to enrolled
	}

	@Test
	fun `Login - Success - Valid and on the allowed list`() = runTest {
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.verifyPassword(validEmail, validPassword) } returns SResult.success(account)
		coEvery { accountsRepository.issueToken(account.id, installId) } returns token

		val result = component().login(validEmail, validPassword, installId)

		assertEquals(LoginResult.Success(token), result)
	}

	@Test
	fun `Login - Failure - Valid, but not on the allowed list`() = runTest {
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account

		val result = component().login(validEmail, validPassword, installId)

		assertIs<LoginResult.Failure>(result)
		// Rejection must short-circuit before a token is minted (and persisted).
		coVerify(exactly = 0) { accountsRepository.verifyPassword(any(), any()) }
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - Failure - Account not found`() = runTest {
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns null
		coEvery { accountsRepository.verifyPassword(validEmail, validPassword) } returns
			SResult.failure("Account not found")

		val result = component().login(validEmail, validPassword, installId)

		assertIs<LoginResult.Failure>(result)
	}

	@Test
	fun `Login - Failure - Bad Login`() = runTest {
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.verifyPassword(validEmail, validPassword) } returns
			SResult.failure("Incorrect password")

		val result = component().login(validEmail, validPassword, installId)

		assertIs<LoginResult.Failure>(result)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - MFA account without a code gets a challenge and no token`() = runTest {
		val (mfaAccount, _) = mfaAccount()

		val result = component().login(mfaAccount.email, validPassword, installId)

		val required = assertIs<LoginResult.MfaRequired>(result)
		assertEquals(listOf(MfaMethod.TOTP, MfaMethod.RECOVERY), required.challenge.methods)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - MFA account with a wrong password never gets a challenge`() = runTest {
		val (mfaAccount, _) = mfaAccount()
		coEvery { accountsRepository.verifyPassword(mfaAccount.email, "wrong") } returns
			SResult.failure("Invalid credentials")

		val result = component().login(mfaAccount.email, "wrong", installId)

		assertIs<LoginResult.Failure>(result)
	}

	@Test
	fun `Login MFA - challenge answered with TOTP issues a token and is consumed`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))

		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)

		assertEquals(LoginResult.Success(token.copy(userId = mfaAccount.id)), result)
		coVerify(exactly = 1) { accountsRepository.issueToken(mfaAccount.id, installId) }

		mfa.now += 30.seconds
		val again = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)
		assertEquals(LoginResult.MfaCodeInvalid(null), again)
	}

	@Test
	fun `Login MFA - wrong code fails and names the account for the audit log`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))

		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret, mfa.now + 5.minutes), installId)

		assertEquals(LoginResult.MfaCodeInvalid(mfaAccount.email), result)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login MFA - expired challenge fails`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))

		mfa.now += MfaRepository.CHALLENGE_LIFETIME + 1.seconds
		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)

		assertEquals(LoginResult.MfaCodeInvalid(null), result)
	}

	@Test
	fun `Login MFA - challenge closes after the attempt cap`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))
		val wrong = mfa.codeAt(enrolled.secret, mfa.now + 5.minutes)

		repeat(MfaRepository.MAX_CHALLENGE_ATTEMPTS) {
			assertEquals(LoginResult.MfaCodeInvalid(mfaAccount.email), component().loginMfa(challenge.challenge.token, wrong, installId))
		}
		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)

		assertEquals(LoginResult.MfaCodeInvalid(null), result)
	}

	@Test
	fun `Login MFA - recovery code works once`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val recovery = enrolled.recoveryCodes.first()

		val first = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))
		assertIs<LoginResult.Success>(component().loginMfa(first.challenge.token, recovery, installId))

		val second = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))
		assertEquals(
			LoginResult.MfaCodeInvalid(mfaAccount.email),
			component().loginMfa(second.challenge.token, recovery, installId)
		)
	}

	@Test
	fun `Login MFA - challenge is bound to the install that asked`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))

		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), "other-install")

		assertEquals(LoginResult.MfaCodeInvalid(null), result)
	}

	@Test
	fun `Login MFA - account soft-deleted after the challenge gets no token`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))
		coEvery { accountsRepository.getAccountOrNull(mfaAccount.id) } returns
			mfaAccount.copy(deleted_at = Instant.parse("2026-07-01T00:00:00Z"))

		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)

		assertIs<LoginResult.Failure>(result)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - inline TOTP code signs in without a challenge`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()

		val result = component().login(mfaAccount.email, validPassword, installId, code = mfa.codeAt(enrolled.secret))

		assertEquals(LoginResult.Success(token.copy(userId = mfaAccount.id)), result)
	}

	@Test
	fun `Login - inline wrong code fails`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()

		val result = component().login(
			mfaAccount.email,
			validPassword,
			installId,
			code = mfa.codeAt(enrolled.secret, mfa.now + 5.minutes),
		)

		assertEquals(LoginResult.MfaCodeInvalid(mfaAccount.email), result)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - the tenth wrong code locks the account and names it for the audit log`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val wrong = mfa.codeAt(enrolled.secret, mfa.now + 5.minutes)
		repeat(MfaRepository.MAX_FAILED_CODES - 1) {
			assertEquals(LoginResult.MfaCodeInvalid(mfaAccount.email), component().login(mfaAccount.email, validPassword, installId, code = wrong))
		}

		val result = component().login(mfaAccount.email, validPassword, installId, code = wrong)

		assertEquals(LoginResult.MfaLocked(mfaAccount.email, mfa.now + MfaRepository.LOCKOUT), result)
		val correct = component().login(mfaAccount.email, validPassword, installId, code = mfa.codeAt(enrolled.secret))
		assertEquals(LoginResult.MfaLocked(mfaAccount.email, mfa.now + MfaRepository.LOCKOUT), correct)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login MFA - a challenge answered while locked reports the lock`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val challenge = assertIs<LoginResult.MfaRequired>(component().login(mfaAccount.email, validPassword, installId))
		val wrong = mfa.codeAt(enrolled.secret, mfa.now + 5.minutes)
		repeat(MfaRepository.MAX_FAILED_CODES) {
			component().login(mfaAccount.email, validPassword, installId, code = wrong)
		}

		val result = component().loginMfa(challenge.challenge.token, mfa.codeAt(enrolled.secret), installId)

		assertEquals(LoginResult.MfaLocked(mfaAccount.email, mfa.now + MfaRepository.LOCKOUT), result)
		coVerify(exactly = 0) { accountsRepository.issueToken(any(), any()) }
	}

	@Test
	fun `Login - a locked account gets the lock instead of a challenge`() = runTest {
		val (mfaAccount, enrolled) = mfaAccount()
		val wrong = mfa.codeAt(enrolled.secret, mfa.now + 5.minutes)
		repeat(MfaRepository.MAX_FAILED_CODES) {
			component().login(mfaAccount.email, validPassword, installId, code = wrong)
		}

		val result = component().login(mfaAccount.email, validPassword, installId)

		assertEquals(LoginResult.MfaLocked(mfaAccount.email, mfa.now + MfaRepository.LOCKOUT), result)
	}

	@Test
	fun `Login - inline code is ignored for an account without MFA`() = runTest {
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.verifyPassword(validEmail, validPassword) } returns SResult.success(account)
		coEvery { accountsRepository.issueToken(account.id, installId) } returns token

		val result = component().login(validEmail, validPassword, installId, code = "123456")

		assertEquals(LoginResult.Success(token), result)
	}

	@Test
	fun `Refresh - unknown user id fails as a normal failure instead of throwing`() = runTest {
		val unknownUserId = 4242L
		coEvery { accountsRepository.getAccountOrNull(unknownUserId) } returns null
		coEvery { accountsRepository.refreshToken(unknownUserId, installId, "bad-refresh") } returns
			SResult.failure("No valid token")

		val result = component().refreshToken(unknownUserId, installId, "bad-refresh")

		assertTrue(result.isFailure)
	}

	@Test
	fun `Refresh - soft-deleted account is rejected with the pending-deletion failure`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(token.userId) } returns
			account.copy(deleted_at = Instant.parse("2026-07-01T00:00:00Z"))

		val result = component().refreshToken(token.userId, installId, "some-refresh")

		assertTrue(result.isFailure)
		assertEquals("Account pending deletion", (result as ServerResult.Failure).error)
		// The gate must short-circuit before a token could be refreshed.
		coVerify(exactly = 0) { accountsRepository.refreshToken(any(), any(), any()) }
	}
}
