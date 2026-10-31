package com.darkrockstudios.apps.hammer.frontend.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RomanNumeralTest {
	@Test
	fun `converts chapter numbers`() {
		assertEquals("I", romanNumeral(1))
		assertEquals("IV", romanNumeral(4))
		assertEquals("IX", romanNumeral(9))
		assertEquals("XIV", romanNumeral(14))
		assertEquals("XL", romanNumeral(40))
		assertEquals("MCMXCIX", romanNumeral(1999))
	}

	@Test
	fun `non-positive numbers stay as digits`() {
		assertEquals("0", romanNumeral(0))
		assertEquals("-3", romanNumeral(-3))
	}
}
