package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.Password_reset_token
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.database.PasswordResetTokenDao
import com.darkrockstudios.apps.hammer.email.EmailResult
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import de.mkammerer.argon2.Argon2Factory
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordResetRepositoryTest : BaseTest() {

	private lateinit var accountDao: AccountDao
	private lateinit var passwordResetTokenDao: PasswordResetTokenDao
	private lateinit var authTokenDao: AuthTokenDao
	private lateinit var emailService: EmailService
	private lateinit var clock: TestClock

	private val b64: Base64 = createTokenBase64()

	private val userId = 1L
	private val email = "writer@example.com"
	private val resetUrl: (String) -> String = { token -> "https://hammer.test/reset?token=$token" }

	@BeforeEach
	override fun setup() {
		super.setup()
		// Relaxed so the QueryResult<Long>-returning mutation methods auto-answer; MockK can't
		// build a default for that generic suspend return when stubbed explicitly.
		accountDao = mockk(relaxed = true)
		passwordResetTokenDao = mockk(relaxed = true)
		authTokenDao = mockk(relaxed = true)
		emailService = mockk()
		setupKoin()
		clock = TestClock(Clock.System)
	}

	private fun repository() = PasswordResetRepository(
		accountDao = accountDao,
		passwordResetTokenDao = passwordResetTokenDao,
		authTokenDao = authTokenDao,
		emailService = emailService,
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
	fun `requestPasswordReset for an unknown account succeeds without creating a token or email`() =
		runTest {
			coEvery { accountDao.findAccount(email) } returns null

			val result = repository().requestPasswordReset(email, resetUrl)

			assertIs<PasswordResetResult.Success>(result)
			coVerify(exactly = 0) { passwordResetTokenDao.createToken(any(), any(), any()) }
			coVerify(exactly = 0) { emailService.sendEmail(any(), any(), any(), any()) }
		}

	@Test
	fun `requestPasswordReset for a known account creates a token and sends an email`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 0
		coEvery { emailService.sendEmail(any(), any(), any(), any()) } returns EmailResult.Success

		val result = repository().requestPasswordReset(email, resetUrl)

		assertIs<PasswordResetResult.Success>(result)
		val storedToken = slot<String>()
		val textBodies = mutableListOf<String?>()
		coVerify(exactly = 1) { passwordResetTokenDao.createToken(userId, capture(storedToken), any()) }
		coVerify(exactly = 1) { emailService.sendEmail(email, any(), any(), captureNullable(textBodies)) }
		// The emailed link must carry the same token that was persisted. (The plain-text
		// body embeds the link verbatim; the HTML body entity-escapes it.)
		assertContains(assertNotNull(textBodies.single()), resetUrl(storedToken.captured))
	}

	@Test
	fun `requestPasswordReset sends no email when no trusted base url is available`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 0

		val result = repository().requestPasswordReset(email) { null }

		assertIs<PasswordResetResult.Success>(result)
		coVerify(exactly = 0) { emailService.sendEmail(any(), any(), any(), any()) }
	}

	@Test
	fun `requestPasswordReset past the rate limit succeeds silently without sending`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 3

		val result = repository().requestPasswordReset(email, resetUrl)

		assertIs<PasswordResetResult.Success>(result)
		coVerify(exactly = 0) { passwordResetTokenDao.createToken(any(), any(), any()) }
		coVerify(exactly = 0) { emailService.sendEmail(any(), any(), any(), any()) }
	}

	@Test
	fun `requestPasswordReset still succeeds when the email fails to send`() = runTest {
		coEvery { accountDao.findAccount(email) } returns account()
		coEvery { passwordResetTokenDao.getRecentTokenCount(userId, any()) } returns 0
		coEvery { emailService.sendEmail(any(), any(), any(), any()) } returns
			EmailResult.Failure("smtp down")

		val result = repository().requestPasswordReset(email, resetUrl)

		assertIs<PasswordResetResult.Success>(result)
		coVerify(exactly = 1) { passwordResetTokenDao.createToken(userId, any(), any()) }
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
			Argon2Factory.create().verify(newHash.captured, "BrandNewPass123".toCharArray()),
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
