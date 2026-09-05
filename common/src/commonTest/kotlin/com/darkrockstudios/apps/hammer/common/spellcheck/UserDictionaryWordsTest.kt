package com.darkrockstudios.apps.hammer.common.spellcheck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserDictionaryWordsTest {

	@Test
	fun `surrounding whitespace is trimmed`() {
		assertEquals("Kvothe", normalizeDictionaryWord("  Kvothe\t"))
	}

	@Test
	fun `case is preserved`() {
		assertEquals("McKinley", normalizeDictionaryWord("McKinley"))
	}

	@Test
	fun `blank input is rejected`() {
		assertNull(normalizeDictionaryWord("   "))
	}

	@Test
	fun `multi-word input is rejected`() {
		assertNull(normalizeDictionaryWord("two words"))
	}

	@Test
	fun `overlong input is rejected`() {
		assertNull(normalizeDictionaryWord("a".repeat(MAX_DICTIONARY_WORD_LENGTH + 1)))
		assertEquals("a".repeat(MAX_DICTIONARY_WORD_LENGTH), normalizeDictionaryWord("a".repeat(MAX_DICTIONARY_WORD_LENGTH)))
	}

	@Test
	fun `cleaning drops invalid entries and dedupes`() {
		assertEquals(
			setOf("alpha", "beta"),
			cleanDictionaryWords(listOf(" alpha ", "", "two words", "beta", "alpha")),
		)
	}
}
