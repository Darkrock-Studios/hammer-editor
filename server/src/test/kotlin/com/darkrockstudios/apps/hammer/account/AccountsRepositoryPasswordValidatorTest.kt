package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MAX_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.account.AccountsRepository.Companion.MIN_PASSWORD_LENGTH
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.MockKAnnotations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Clock

class AccountsRepositoryPasswordValidatorTest {

	private lateinit var clock: TestClock
	private lateinit var b64: Base64
	private lateinit var secureRandom: SecureRandom

	@BeforeEach
	fun begin() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		clock = TestClock(Clock.System)
		b64 = createTokenBase64()
		secureRandom = SecureRandom()
	}

	@Test
	fun `Valid Password`() {
		val result = AccountsRepository.validatePassword("qweasdZXC123!@#")
		assertEquals(AccountsRepository.Companion.PasswordResult.VALID, result)
	}

	@Test
	fun `Valid Password - Trimmed`() {
		val padding = " ".repeat(MAX_PASSWORD_LENGTH)
		val result = AccountsRepository.validatePassword(padding + "qweasdZXC123!@#" + padding)
		assertEquals(AccountsRepository.Companion.PasswordResult.VALID, result)
	}

	@Test
	fun `Invalid Password - Too Short`() {
		val passwd = "a".repeat(MIN_PASSWORD_LENGTH - 1)
		val result = AccountsRepository.validatePassword(passwd)
		assertEquals(AccountsRepository.Companion.PasswordResult.TOO_SHORT, result)
	}

	@Test
	fun `Invalid Password - Too Long`() {
		val passwd = "a".repeat(MAX_PASSWORD_LENGTH + 1)
		val result = AccountsRepository.validatePassword(passwd)
		assertEquals(AccountsRepository.Companion.PasswordResult.TOO_LONG, result)
	}
}