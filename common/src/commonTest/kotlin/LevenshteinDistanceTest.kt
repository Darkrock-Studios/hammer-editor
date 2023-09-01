package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.util.levenshteinDistance
import kotlin.test.Test
import kotlin.test.assertEquals

class LevenshteinDistanceTest {
	@Test
	fun `Distance Test`() {
		testDistance("", "", 0)
		testDistance("1", "1", 0)
		testDistance("1", "2", 1)
		testDistance("12", "12", 0)
		testDistance("123", "12", 1)
		testDistance("1234", "1", 3)
		testDistance("1234", "1233", 1)
		testDistance("", "12345", 5)
		testDistance("kitten", "mittens", 2)
		testDistance("canada", "canad", 1)
		testDistance("canad", "canada", 1)
	}

	private fun testDistance(a: String, b: String, expectedDistance: Int) {
		val d = levenshteinDistance(a, b)
		assertEquals(expectedDistance, d, "Distance did not match for `$a` and `$b`")
	}
}