package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.Password_reset_token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.utilities.Argon2PasswordHasher
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordResetRepositoryTest : BaseTest() {

	private lateinit var accountDao: AccountDao
	private lateinit var passwordResetTokenDao: PasswordResetTokenDao
	private lateinit var authTokenDao: AuthTokenDao
	private lateinit var clock: TestClock

	private val b64: Base64 = createTokenBase64()

	private val userId = 1L
	private val email = "writer@example.com"

	@BeforeEach
	override fun setup() {
		super.setup()
		// Relaxed so the QueryResult<Long>-returning mutation methods auto-answer; MockK can't
		// build a default for that generic suspend return when stubbed explicitly.
		accountDao = mockk(relaxed = true)
		passwordResetTokenDao = mockk(relaxed = true)
		authTokenDao = mockk(relaxed = true)
		setupKoin()
		clock = TestClock(Clock.System)
	}

	private fun repository() = PasswordResetRepository(
		accountDao = accountDao,
		passwordResetTokenDao = passwordResetTokenDao,
		authTokenDao = authTokenDao,
		clock = clock,
		base64 = b64,
	)

	private fun account() = Account(
		id = userId,
		email = email,
		password_hash = AccountsRepository.hashPassword("oldPassword123"),
		cipher_secret = "secret",
		created = clock.now(),
		is_admin = false,
		last_sync = clock.now(),
		pen_name = null,
		bio = null,
		email_verified = true,
		community_member = false,
		deleted_at = null,
	)

	private fun resetToken(
		token: String = "the-token",
		expires: kotlin.time.Instant = clock.now() + 10.minutes,
		used: Boolean = false,
	) = Password_reset_token(
		id = 99L,
		user_id = userId,
		token = token,
		created = clock.now(),
		expires = expires,
		used = used,
	)

	@Test
	fun `createResetToken for an unknown account stores nothing`() = runTest {
		coEvery { accountDao.findAccount(email) } returns null

		assertNull(repository().createResetToken(email))

		coVerify(exactly = 0) { passwordResetTokenDao.createToken(any(), any(), any()) }
	}

	@Test
	fun `createResetToken for a known account stores and returns the token`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 0

		val token = repository().createResetToken(email)

		val storedToken = slot<String>()
		coVerify(exactly = 1) { passwordResetTokenDao.createToken(userId, capture(storedToken), any()) }
		assertEquals(storedToken.captured, token)
	}

	@Test
	fun `createResetToken past the rate limit stores nothing`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 3

		assertNull(repository().createResetToken(email))

		coVerify(exactly = 0) { passwordResetTokenDao.createToken(any(), any(), any()) }
	}

	@Test
	fun `validateResetToken returns Invalid for an unknown token`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("nope") } returns null

		assertIs<TokenValidationResult.Invalid>(repository().validateResetToken("nope"))
	}

	@Test
	fun `validateResetToken returns AlreadyUsed for a consumed token`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns resetToken(used = true)

		assertIs<TokenValidationResult.AlreadyUsed>(repository().validateResetToken("t"))
	}

	@Test
	fun `validateResetToken returns Expired for a token past its lifetime`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns
			resetToken(expires = clock.now() - 1.minutes)

		assertIs<TokenValidationResult.Expired>(repository().validateResetToken("t"))
	}

	@Test
	fun `validateResetToken returns Valid with the user id for a live token`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns resetToken()

		val result = repository().validateResetToken("t")

		assertEquals(userId, assertIs<TokenValidationResult.Valid>(result).userId)
	}

	@Test
	fun `resetPassword fails for an invalid token`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns null

		val result = repository().resetPassword("t", "BrandNewPass123")

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { accountDao.updatePassword(any(), any()) }
	}

	@Test
	fun `resetPassword fails for a weak password without touching the account`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns resetToken()

		val result = repository().resetPassword("t", "short")

		assertTrue(isFailure(result))
		assertIs<InvalidPassword>(result.exception)
		coVerify(exactly = 0) { accountDao.updatePassword(any(), any()) }
	}

	@Test
	fun `resetPassword updates the password, logs out devices, and consumes the token`() = runTest {
		coEvery { passwordResetTokenDao.getTokenByToken("t") } returns resetToken()

		val result = repository().resetPassword("t", "BrandNewPass123")

		assertTrue(isSuccess(result))
		val newHash = slot<String>()
		coVerify(exactly = 1) { accountDao.updatePassword(userId, capture(newHash)) }
		assertTrue(
			Argon2PasswordHasher.verify(newHash.captured, "BrandNewPass123".toCharArray()),
			"Stored hash must verify against the new password",
		)
		coVerify(exactly = 1) { authTokenDao.deleteTokensByUserId(userId) }
		coVerify(exactly = 1) { passwordResetTokenDao.markTokenAsUsed("t") }
	}

	@Test
	fun `cleanupExpiredTokens delegates to the dao`() = runTest {

		repository().cleanupExpiredTokens()

		coVerify(exactly = 1) { passwordResetTokenDao.deleteExpiredTokens() }
	}
}
