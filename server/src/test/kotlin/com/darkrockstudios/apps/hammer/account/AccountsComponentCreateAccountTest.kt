package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountsComponentCreateAccountTest {

	@MockK
	private lateinit var accountsRepository: AccountsRepository

	@MockK
	private lateinit var whiteListRepository: WhiteListRepository

	@MockK
	private lateinit var projectsRepository: ProjectsRepository

	private val validEmail = "test@test.com"
	private val validPassword = "qweasdZXC123"
	private val installId = "123456789"
	private val token = Token(
		userId = 2,
		auth = "123",
		refresh = "abc"
	)

	@Before
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
	}

	@Test
	fun `Create Account - First User, skip whitelist`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns false
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)

		coVerify(exactly = 0) { whiteListRepository.useWhiteList() }
		coVerify(exactly = 0) { whiteListRepository.isOnWhiteList(any()) }
		confirmVerified(whiteListRepository)

		coVerify { projectsRepository.createUserData(token.userId) }
	}

	@Test
	fun `Create Account - Success`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)

		coVerify { projectsRepository.createUserData(token.userId) }
	}

	@Test
	fun `Create Account - Failure - Not On Whitelist`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(isFailure(result))
	}

	@Test
	fun `Create Account - Success - Is On Whitelist`() = runTest {
		coEvery { accountsRepository.hasUsers() } returns true
		coEvery { accountsRepository.findAccount(any()) } returns null
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery {
			accountsRepository.createAccount(
				email = validEmail,
				installId = installId,
				password = validPassword
			)
		} returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.createAccount(
			email = validEmail,
			installId = installId,
			password = validPassword,
		)

		assertTrue(isSuccess(result))
	}
}