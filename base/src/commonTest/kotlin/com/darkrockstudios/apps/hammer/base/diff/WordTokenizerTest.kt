package com.darkrockstudios.apps.hammer.base.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordTokenizerTest {

	@Test
	fun `empty input produces no tokens`() {
		assertTrue(tokenize("").isEmpty())
	}

	@Test
	fun `words are separated by whitespace tokens`() {
		val tokens = tokenize("hello world")
		assertEquals(listOf("hello", " ", "world"), tokens.map { it.text })
	}

	@Test
	fun `consecutive whitespace becomes a single token`() {
		val tokens = tokenize("a   b")
		assertEquals(listOf("a", "   ", "b"), tokens.map { it.text })
	}

	@Test
	fun `punctuation is emitted as single-char tokens`() {
		val tokens = tokenize("Hello, world!")
		assertEquals(listOf("Hello", ",", " ", "world", "!"), tokens.map { it.text })
	}

	@Test
	fun `apostrophe is kept inside a word so contractions stay together`() {
		val tokens = tokenize("don't stop")
		assertEquals(listOf("don't", " ", "stop"), tokens.map { it.text })
	}

	@Test
	fun `newlines are part of the whitespace token`() {
		val tokens = tokenize("para one\n\npara two")
		assertEquals(listOf("para", " ", "one", "\n\n", "para", " ", "two"), tokens.map { it.text })
	}

	@Test
	fun `token offsets reconstruct the original text`() {
		val text = "The quick, brown fox.\nJumped!"
		val tokens = tokenize(text)
		val rebuilt = buildString {
			for (token in tokens) {
				assertEquals(length, token.plainStart, "plainStart of '${token.text}' should equal current length")
				append(token.text)
			}
		}
		assertEquals(text, rebuilt)
	}
}
