package com.darkrockstudios.apps.hammer.base.validate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectNameValidatorTest {

	@Test
	fun validName() {
		assertEquals(ProjectNameValidationResult.VALID, ProjectNameValidator.validate("My Project"))
	}

	@Test
	fun validNameWithAllowedPunctuation() {
		assertEquals(
			ProjectNameValidationResult.VALID,
			ProjectNameValidator.validate("Alice's Adventures (Draft 2) - Notes!")
		)
	}

	@Test
	fun validNameWithUnicodeLetters() {
		assertEquals(ProjectNameValidationResult.VALID, ProjectNameValidator.validate("夏目漱石の物語"))
		assertEquals(ProjectNameValidationResult.VALID, ProjectNameValidator.validate("Élodie's Élégie"))
	}

	@Test
	fun nullName() {
		assertEquals(ProjectNameValidationResult.NULL, ProjectNameValidator.validate(null))
	}

	@Test
	fun blankName() {
		assertEquals(ProjectNameValidationResult.BLANK, ProjectNameValidator.validate(""))
		assertEquals(ProjectNameValidationResult.BLANK, ProjectNameValidator.validate("    "))
	}

	@Test
	fun nameWithDisallowedCharacters() {
		for (name in listOf("Bad#Name", "tag@home", "100%", "a;b", "new\nline", "a~b")) {
			assertEquals(
				ProjectNameValidationResult.INVALID_CHARACTERS,
				ProjectNameValidator.validate(name),
				"expected '$name' to be rejected",
			)
		}
	}

	@Test
	fun leadingDotIsRejected() {
		assertEquals(ProjectNameValidationResult.INVALID_CHARACTERS, ProjectNameValidator.validate(".hidden"))
	}

	@Test
	fun trailingDotIsRejected() {
		assertEquals(ProjectNameValidationResult.INVALID_CHARACTERS, ProjectNameValidator.validate("name."))
	}

	@Test
	fun trailingSpaceIsRejected() {
		assertEquals(ProjectNameValidationResult.INVALID_CHARACTERS, ProjectNameValidator.validate("name "))
	}

	@Test
	fun windowsReservedNamesAreRejected() {
		for (name in listOf("CON", "con", "PRN", "nul", "COM0", "COM1", "LPT0", "LPT9", "AUX.txt")) {
			assertEquals(
				ProjectNameValidationResult.INVALID_CHARACTERS,
				ProjectNameValidator.validate(name),
				"expected reserved name '$name' to be rejected",
			)
		}
	}

	@Test
	fun reservedNamesAllowedWhenNotRawFilename() {
		for (name in listOf("CON", "con", "PRN", "nul", "COM0", "COM1", "LPT0", "LPT9", "AUX.txt")) {
			assertEquals(
				ProjectNameValidationResult.VALID,
				ProjectNameValidator.validate(name, usedAsRawFilename = false),
				"expected reserved name '$name' to be allowed for a wrapped (non-raw) name",
			)
		}
	}

	@Test
	fun leadingDotAllowedWhenNotRawFilename() {
		assertEquals(
			ProjectNameValidationResult.VALID,
			ProjectNameValidator.validate(".prologue", usedAsRawFilename = false),
		)
	}

	@Test
	fun trailingDotAndSpaceStillRejectedWhenNotRawFilename() {
		// The on-disk encoder strips a trailing '.'/' ', so the title could never keep them.
		assertEquals(
			ProjectNameValidationResult.INVALID_CHARACTERS,
			ProjectNameValidator.validate("Chapter 1.", usedAsRawFilename = false),
		)
		assertEquals(
			ProjectNameValidationResult.INVALID_CHARACTERS,
			ProjectNameValidator.validate("Chapter 1 ", usedAsRawFilename = false),
		)
	}

	@Test
	fun disallowedCharactersStillRejectedWhenNotRawFilename() {
		for (name in listOf("Bad#Name", "a~b")) {
			assertEquals(
				ProjectNameValidationResult.INVALID_CHARACTERS,
				ProjectNameValidator.validate(name, usedAsRawFilename = false),
				"expected '$name' to be rejected",
			)
		}
	}

	@Test
	fun nameAtMaxLengthIsValid() {
		assertEquals(
			ProjectNameValidationResult.VALID,
			ProjectNameValidator.validate("a".repeat(ProjectNameValidator.MAX_LENGTH)),
		)
	}

	@Test
	fun nameOverMaxLengthIsTooLong() {
		assertEquals(
			ProjectNameValidationResult.TOO_LONG,
			ProjectNameValidator.validate("a".repeat(ProjectNameValidator.MAX_LENGTH + 1)),
		)
	}

	@Test
	fun validateProjectNameWrapperMatchesValidate() {
		assertTrue(validateProjectName("My Project"))
		assertFalse(validateProjectName(null))
		assertFalse(validateProjectName(""))
		assertFalse(validateProjectName("Bad#Name"))
		assertFalse(validateProjectName("a".repeat(MAX_PROJECT_NAME_LENGTH + 1)))
	}

	@Test
	fun isCharacterAllowedMatchesValidation() {
		assertTrue(ProjectNameValidator.isCharacterAllowed('a'))
		assertTrue(ProjectNameValidator.isCharacterAllowed('-'))
		assertFalse(ProjectNameValidator.isCharacterAllowed('#'))
		assertFalse(ProjectNameValidator.isCharacterAllowed('~'))
	}
}
