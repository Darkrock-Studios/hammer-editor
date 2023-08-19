package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountsComponentLoginTest {

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
	private val account = Account(
		id = token.userId,
		email = validEmail,
		salt = "123",
		password_hash = "asd123s",
		created = "11 oclock",
		isAdmin = false
	)

	@Before
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
	}

	@Test
	fun `Login - Success`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.login(
			email = validEmail,
			password = validPassword,
			installId = installId
		) } returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)
	}

	@Test
	fun `Login - Success - Valid, whitelist enabled and one it`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns true
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.login(
			email = validEmail,
			password = validPassword,
			installId = installId
		) } returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isSuccess(result))
		assertEquals(token, result.data)
	}

	@Test
	fun `Login - Failure - Valid, but not on whitelist`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns true
		coEvery { whiteListRepository.isOnWhiteList(validEmail) } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.login(
			email = validEmail,
			password = validPassword,
			installId = installId
		) } returns SResult.success(token)

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
	}

	@Test
	fun `Login - Failure - Account not found`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns null
		coEvery { accountsRepository.login(
			email = validEmail,
			password = validPassword,
			installId = installId
		) } returns SResult.failure("Account not found")

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
	}

	@Test
	fun `Login - Failure - Bad Login`() = runTest {
		coEvery { whiteListRepository.useWhiteList() } returns false
		coEvery { accountsRepository.findAccount(validEmail) } returns account
		coEvery { accountsRepository.login(
			email = validEmail,
			password = validPassword,
			installId = installId
		) } returns SResult.failure("Incorrect password")

		val comp = AccountsComponent(accountsRepository, whiteListRepository, projectsRepository)
		val result = comp.login(validEmail, validPassword, installId)

		assertTrue(isFailure(result))
	}
}