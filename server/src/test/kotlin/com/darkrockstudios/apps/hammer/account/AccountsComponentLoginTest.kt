package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AccountsComponentLoginTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK
	private lateinit var projectsRepository: ProjectsRepository

	@MockK
	private lateinit var configRepository: ConfigRepository

	@MockK
	private lateinit var termsOfServiceRepository: TermsOfServiceRepository

	private val serverConfig = ServerConfig()

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
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
	}

	@Test
	fun `Login - Success`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery {
			accountsRepository.login(
				email = validEmail,
				password = validPassword,
				installId = installId
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)
	}

	@Test
	fun `Login - Success - Valid, whitelist enabled and one it`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery {
			accountsRepository.login(
				email = validEmail,
				password = validPassword,
				installId = installId
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)
	}

	@Test
	fun `Login - Failure - Valid, but not on whitelist`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery {
			accountsRepository.login(
				email = validEmail,
				password = validPassword,
				installId = installId
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
		// Rejection must short-circuit before login mints (and persists) a token.
		coVerify(exactly = 0) { accountsRepository.login(any(), any(), any()) }
	}

	@Test
	fun `Login - Failure - Account not found`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns null
		coEvery {
			accountsRepository.login(
				email = validEmail,
				password = validPassword,
				installId = installId
			)
		} returns SResult.failure("Account not found")

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
	}

	@Test
	fun `Refresh - unknown user id fails as a normal failure instead of throwing`() = runTest {
		val unknownUserId = 4242L
		coEvery { accountsRepository.getAccountOrNull(unknownUserId) } returns null
		coEvery { accountsRepository.refreshToken(unknownUserId, installId, "bad-refresh") } returns
			SResult.failure("No valid token")

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)

		val result = comp.refreshToken(unknownUserId, installId, "bad-refresh")

		assertTrue(isFailure(result))
	}

	@Test
	fun `Refresh - soft-deleted account is rejected with the pending-deletion failure`() = runTest {
		coEvery { accountsRepository.getAccountOrNull(token.userId) } returns
			account.copy(deleted_at = Instant.parse("2026-07-01T00:00:00Z"))

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)

		val result = comp.refreshToken(token.userId, installId, "some-refresh")

		assertTrue(isFailure(result))
		assertEquals("Account pending deletion", result.error)
		// The gate must short-circuit before a token could be refreshed.
		coVerify(exactly = 0) { accountsRepository.refreshToken(any(), any(), any()) }
	}

	@Test
	fun `Login - Failure - Bad Login`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery {
			accountsRepository.login(
				email = validEmail,
				password = validPassword,
				installId = installId
			)
		} returns SResult.failure("Incorrect password")

		val comp = AccountsComponent(
			accountsRepository,
			whiteListRepository,
			projectsRepository,
			configRepository,
			termsOfServiceRepository,
			serverConfig
		)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
	}
}