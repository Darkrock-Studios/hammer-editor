package com.darkrockstudios.apps.hammer.common.data.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ManuscriptWordCountTest {

	private fun assertRounds(expected: Int, count: Int) =
		assertEquals(expected, manuscriptWordCount(count), "manuscriptWordCount($count)")

	@Test
	fun `counts under 100 are exact`() {
		assertRounds(0, 0)
		assertRounds(1, 1)
		assertRounds(99, 99)
	}

	@Test
	fun `counts below 17500 round half up to the nearest 100`() {
		assertRounds(100, 100)
		assertRounds(100, 149)
		assertRounds(200, 150)
		assertRounds(17_400, 17_449)
		assertRounds(17_500, 17_450)
	}

	@Test
	fun `counts below 40000 round half up to the nearest 500`() {
		assertRounds(17_500, 17_500)
		assertRounds(39_500, 39_749)
		assertRounds(40_000, 39_750)
	}

	@Test
	fun `counts from 40000 round half up to the nearest 1000`() {
		assertRounds(40_000, 40_000)
		assertRounds(40_000, 40_499)
		assertRounds(41_000, 40_500)
		assertRounds(123_000, 123_456)
	}
}
