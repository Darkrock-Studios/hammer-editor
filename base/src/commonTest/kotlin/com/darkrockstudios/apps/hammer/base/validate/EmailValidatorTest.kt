package com.darkrockstudios.apps.hammer.base.validate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailValidatorTest {

	@Test
	fun validSimpleEmail() {
		assertTrue(EmailValidator.validate("test@example.com"))
	}

	@Test
	fun validEmailWithSubdomain() {
		assertTrue(EmailValidator.validate("test@mail.example.com"))
	}

	@Test
	fun validEmailWithPlus() {
		assertTrue(EmailValidator.validate("test+tag@example.com"))
	}

	@Test
	fun validEmailWithNumbers() {
		assertTrue(EmailValidator.validate("test123@example.com"))
	}

	@Test
	fun validEmailWithDots() {
		assertTrue(EmailValidator.validate("first.last@example.com"))
	}

	@Test
	fun validEmailWithUnderscore() {
		assertTrue(EmailValidator.validate("first_last@example.com"))
	}

	@Test
	fun validEmailWithHyphen() {
		assertTrue(EmailValidator.validate("first-last@example.com"))
	}

	@Test
	fun validEmailWithLeadingWhitespace() {
		// Whitespace should be trimmed
		assertTrue(EmailValidator.validate("  test@example.com"))
	}

	@Test
	fun validEmailWithTrailingWhitespace() {
		// Whitespace should be trimmed
		assertTrue(EmailValidator.validate("test@example.com  "))
	}

	@Test
	fun invalidEmptyEmail() {
		assertFalse(EmailValidator.validate(""))
	}

	@Test
	fun invalidWhitespaceOnlyEmail() {
		assertFalse(EmailValidator.validate("   "))
	}

	@Test
	fun invalidNoAtSymbol() {
		assertFalse(EmailValidator.validate("testexample.com"))
	}

	@Test
	fun invalidNoDomain() {
		assertFalse(EmailValidator.validate("test@"))
	}

	@Test
	fun invalidNoLocalPart() {
		assertFalse(EmailValidator.validate("@example.com"))
	}

	@Test
	fun invalidMultipleAtSymbols() {
		assertFalse(EmailValidator.validate("test@@example.com"))
	}
}
