package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MAX_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MIN_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.AuthTokenDao
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AccountsRepositoryPasswordValidatorTest {

	@MockK
	private lateinit var accountDao: AccountDao

	@MockK
	private lateinit var authTokenDao: AuthTokenDao

	private lateinit var clock: TestClock

	@BeforeEach
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		clock = TestClock(Clock.System)
	}

	@Test
	fun `Valid Password`() {
		val repo = AccountsRepository(accountDao, authTokenDao, clock)
		val result = repo.validatePassword("qweasdZXC123!@#")
		assertEquals(AccountsRepository.Companion.PasswordResult.VALID, result)
	}

	@Test
	fun `Valid Password - Trimmed`() {
		val repo = AccountsRepository(accountDao, authTokenDao, clock)
		val padding = " ".repeat(MAX_PASSWORD_LENGTH)
		val result = repo.validatePassword(padding + "qweasdZXC123!@#" + padding)
		assertEquals(AccountsRepository.Companion.PasswordResult.VALID, result)
	}

	@Test
	fun `Invalid Password - Too Short`() {
		val repo = AccountsRepository(accountDao, authTokenDao, clock)
		val passwd = "a".repeat(MIN_PASSWORD_LENGTH - 1)
		val result = repo.validatePassword(passwd)
		assertEquals(AccountsRepository.Companion.PasswordResult.TOO_SHORT, result)
	}

	@Test
	fun `Invalid Password - Too Long`() {
		val repo = AccountsRepository(accountDao, authTokenDao, clock)
		val passwd = "a".repeat(MAX_PASSWORD_LENGTH + 1)
		val result = repo.validatePassword(passwd)
		assertEquals(AccountsRepository.Companion.PasswordResult.TOO_LONG, result)
	}
}