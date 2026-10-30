package com.darkrockstudios.apps.hammer.base.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The cases in words.txt (resources), which the plugin SDKs' words() tests read too. */
class SharedWordCasesTest {
	private val escape = Regex("""\\u\{([0-9A-Fa-f]+)}""")

	private fun unescape(s: String) = escape.replace(s) {
		StringBuilder().appendCodePoint(it.groupValues[1].toInt(16)).toString()
	}

	@Test
	fun `words split the shared cases`() {
		val lines = readTestResource("words.txt").lines().filter { it.isNotBlank() && !it.startsWith("#") }
		assertTrue(lines.isNotEmpty())
		lines.forEach { line ->
			val (text, expected) = line.split('\t').map(::unescape)
			assertEquals(expected.split(' ').filter { it.isNotEmpty() }, words(text), line)
			val boundaries = wordBoundaries(text).toSet()
			wordSpans(text).forEach { span ->
				assertTrue(span.first in boundaries && span.last + 1 in boundaries, "$span on $line")
			}
		}
	}
}
