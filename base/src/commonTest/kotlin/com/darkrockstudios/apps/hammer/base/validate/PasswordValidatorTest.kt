package com.darkrockstudios.apps.hammer.base.validate

import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordValidatorTest {

	@Test
	fun validPassword() {
		val result = PasswordValidator.validate("ValidPass123")
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun passwordExactlyMinLength() {
		val result = PasswordValidator.validate("12345678") // exactly 8 chars
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun passwordExactlyMaxLength() {
		val result = PasswordValidator.validate("a".repeat(64)) // exactly 64 chars
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun passwordTooShort() {
		val result = PasswordValidator.validate("Short1")
		assertEquals(PasswordValidationResult.TOO_SHORT, result)
	}

	@Test
	fun passwordTooShortEmpty() {
		val result = PasswordValidator.validate("")
		assertEquals(PasswordValidationResult.TOO_SHORT, result)
	}

	@Test
	fun passwordTooShortWhitespaceOnly() {
		val result = PasswordValidator.validate("       ")
		assertEquals(PasswordValidationResult.TOO_SHORT, result)
	}

	@Test
	fun passwordTooLong() {
		val result = PasswordValidator.validate("a".repeat(65)) // 65 chars
		assertEquals(PasswordValidationResult.TOO_LONG, result)
	}

	@Test
	fun passwordWithLeadingWhitespace() {
		// Whitespace should be trimmed, so "  12345678" becomes "12345678"
		val result = PasswordValidator.validate("  12345678")
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun passwordWithTrailingWhitespace() {
		// Whitespace should be trimmed
		val result = PasswordValidator.validate("12345678  ")
		assertEquals(PasswordValidationResult.VALID, result)
	}

	@Test
	fun passwordJustUnderMinLength() {
		val result = PasswordValidator.validate("1234567") // 7 chars
		assertEquals(PasswordValidationResult.TOO_SHORT, result)
	}

	@Test
	fun passwordJustOverMaxLength() {
		val result = PasswordValidator.validate("a".repeat(65)) // 65 chars
		assertEquals(PasswordValidationResult.TOO_LONG, result)
	}
}
