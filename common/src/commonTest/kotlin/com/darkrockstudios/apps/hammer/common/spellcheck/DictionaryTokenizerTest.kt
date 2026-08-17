package com.darkrockstudios.apps.hammer.common.spellcheck

import kotlin.test.Test
import kotlin.test.assertEquals

class DictionaryTokenizerTest {

	@Test
	fun `multi-word names are split into single tokens`() {
		assertEquals(
			setOf("bob", "roberts"),
			tokenizeDictionaryWords(listOf("Bob Roberts")),
		)
	}

	@Test
	fun `tokens are lowercased`() {
		assertEquals(
			setOf("zaltharion"),
			tokenizeDictionaryWords(listOf("ZALTHARION")),
		)
	}

	@Test
	fun `surrounding punctuation is stripped`() {
		assertEquals(
			setOf("mr", "smith"),
			tokenizeDictionaryWords(listOf("Mr. Smith")),
		)
	}

	@Test
	fun `inner apostrophes and hyphens are kept`() {
		assertEquals(
			setOf("d'artagnan", "jean-luc"),
			tokenizeDictionaryWords(listOf("d'Artagnan", "Jean-Luc")),
		)
	}

	@Test
	fun `blank and single-character tokens are dropped`() {
		assertEquals(
			setOf("initial"),
			tokenizeDictionaryWords(listOf("A Initial", "  ", "-", "x")),
		)
	}

	@Test
	fun `duplicate tokens across phrases are deduped case-insensitively`() {
		assertEquals(
			setOf("kastle", "rock"),
			tokenizeDictionaryWords(listOf("Kastle Rock", "kastle", "KASTLE")),
		)
	}

	@Test
	fun `empty input produces empty output`() {
		assertEquals(emptySet(), tokenizeDictionaryWords(emptyList()))
	}
}
