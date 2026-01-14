package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AccountsRepositoryPasswordValidatorTest {

	@Test
	fun `Valid Password`() {
		val result = PasswordValidator.validate("qweasdZXC123!@#")
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun `Valid Password - Trimmed`() {
		val padding = " ".repeat(PasswordValidator.MAX_LENGTH)
		val result = PasswordValidator.validate(padding + "qweasdZXC123!@#" + padding)
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun `Invalid Password - Too Short`() {
		val passwd = "a".repeat(PasswordValidator.MIN_LENGTH - 1)
		val result = PasswordValidator.validate(passwd)
		assertEquals(PasswordValidationResult.TOO_SHORT, result)
	}

	@Test
	fun `Invalid Password - Too Long`() {
		val passwd = "a".repeat(PasswordValidator.MAX_LENGTH + 1)
		val result = PasswordValidator.validate(passwd)
		assertEquals(PasswordValidationResult.TOO_LONG, result)
	}
}