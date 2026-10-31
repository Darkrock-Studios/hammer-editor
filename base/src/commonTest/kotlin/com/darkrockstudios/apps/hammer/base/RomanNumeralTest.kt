package com.darkrockstudios.apps.hammer.base

import kotlin.test.Test
import kotlin.test.assertEquals

class RomanNumeralTest {
	@Test
	fun convertsChapterNumbers() {
		assertEquals("I", romanNumeral(1))
		assertEquals("IV", romanNumeral(4))
		assertEquals("IX", romanNumeral(9))
		assertEquals("XIV", romanNumeral(14))
		assertEquals("XL", romanNumeral(40))
		assertEquals("MCMXCIX", romanNumeral(1999))
	}

	@Test
	fun zeroAndNegatives() {
		assertEquals("0", romanNumeral(0))
		assertEquals("III", romanNumeral(-3))
	}
}
