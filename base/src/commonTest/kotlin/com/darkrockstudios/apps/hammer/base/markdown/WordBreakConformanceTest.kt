package com.darkrockstudios.apps.hammer.base.markdown

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every case in Unicode's WordBreakTest.txt (resources), which is the conformance test for UAX #29. */
class WordBreakConformanceTest {

	private class Case(val text: String, val boundaries: List<Int>, val line: String)

	private fun parse(line: String): Case? {
		val rule = line.substringBefore('#').trim()
		if (rule.isEmpty()) return null
		val text = StringBuilder()
		val boundaries = mutableListOf<Int>()
		for (token in rule.split(' ', '\t').filter { it.isNotEmpty() }) {
			when (token) {
				"÷" -> boundaries += text.length
				"×" -> Unit
				else -> text.appendCodePoint(token.toInt(16))
			}
		}
		return Case(text.toString(), boundaries, line)
	}

	@Test
	fun `boundaries match every WordBreakTest case`() {
		val cases = readTestResource("WordBreakTest.txt").lines().mapNotNull(::parse)
		assertTrue(cases.size > 1800, "Only ${cases.size} cases parsed")

		val failures = cases.filter { wordBoundaries(it.text).toList() != it.boundaries }
		if (failures.isNotEmpty()) {
			val shown = failures.take(10).joinToString("\n") { case ->
				"expected ${case.boundaries}, got ${wordBoundaries(case.text).toList()}: ${case.line}"
			}
			fail("${failures.size} of ${cases.size} cases fail:\n$shown")
		}
	}
}
