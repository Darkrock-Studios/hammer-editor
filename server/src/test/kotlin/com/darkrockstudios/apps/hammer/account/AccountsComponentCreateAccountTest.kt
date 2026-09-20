package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountsComponentCreateAccountTest {

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

	private fun component() = AccountsComponent(
		accountsRepository,
		whiteListRepository,
		projectsRepository,
		termsOfServiceRepository,
		PluginRegistry(emptyList())
	)

	@BeforeEach
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		every { termsOfServiceRepository.challenge() } returns null
	}

	@Test
	fun `Create Account - First User, skip whitelist`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns false
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(result is CreateAccountResult.Success)
		assertEquals(token, result.token)

		coVerify(exactly = 0) { whiteListRepository.isOnWhiteList(any()) }

		coVerify { projectsRepository.createUserData(token.userId) }
	}

	@Test
	fun `Create Account - Success - Is On Allowed List`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(result is CreateAccountResult.Success)
		assertEquals(token, result.token)

		coVerify { projectsRepository.createUserData(token.userId) }
	}

	@Test
	fun `Create Account - Failure - Not On Allowed List`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(result is CreateAccountResult.Failure)
		// A whitelist rejection must short-circuit: no account row, no user data.
		coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
		coVerify(exactly = 0) { projectsRepository.createUserData(any()) }
	}

	@Test
	fun `Create Account - TOS enforced, no acceptance - challenges`() = runTest {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		every { termsOfServiceRepository.challenge() } returns challenge

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(result is CreateAccountResult.TermsRequired)
		assertEquals(challenge, result.challenge)
		// No account is created until the terms are accepted.
		coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
		coVerify(exactly = 0) { projectsRepository.createUserData(any()) }
	}

	@Test
	fun `Create Account - TOS enforced, stale acceptance - challenges`() = runTest {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v2")
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		every { termsOfServiceRepository.challenge() } returns challenge

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
			acceptedTosVersion = "v1",
		)

		assertTrue(result is CreateAccountResult.TermsRequired)
		assertEquals(challenge, result.challenge)
		coVerify(exactly = 0) { accountsRepository.createAccount(any(), any(), any()) }
	}

	@Test
	fun `Create Account - TOS enforced, correct acceptance - succeeds`() = runTest {
		val challenge = TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		every { termsOfServiceRepository.challenge() } returns challenge
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
			acceptedTosVersion = "v1",
		)

		assertTrue(result is CreateAccountResult.Success)
		assertEquals(token, result.token)
		coVerify { projectsRepository.createUserData(token.userId) }
	}

	@Test
	fun `Create Account - Whitelist rejection precedes TOS challenge`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		every { termsOfServiceRepository.challenge() } returns
			TermsOfServiceChallenge(text = "Be excellent to each other", version = "v1")

		val result = component().createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		// Whitelist is checked first, so non-whitelisted emails never see the TOS.
		assertTrue(result is CreateAccountResult.Failure)
		verify(exactly = 0) { termsOfServiceRepository.challenge() }
	}
}
