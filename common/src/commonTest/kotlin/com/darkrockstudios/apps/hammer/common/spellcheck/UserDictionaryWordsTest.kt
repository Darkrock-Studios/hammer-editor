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
	fun `decomposed characters are composed`() {
		assertEquals("\u00e8", normalizeDictionaryWord("e\u0300"))
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
	fun `stored words are normalized and deduped`() {
		assertEquals(
			setOf("alpha", "beta", "\u00e8"),
			normalizeStoredDictionaryWords(listOf(" alpha ", "", "   ", "beta", "alpha", "e\u0300")),
		)
	}

	@Test
	fun `stored words a peer wrote under laxer rules are kept`() {
		val overlong = "a".repeat(MAX_DICTIONARY_WORD_LENGTH + 1)

		assertEquals(
			setOf("two words", overlong),
			normalizeStoredDictionaryWords(listOf("two words", overlong)),
		)
	}
}
