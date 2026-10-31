package com.darkrockstudios.apps.hammer.frontend

import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals

class ReadingTimeTest {
	@Test
	fun `minutes under an hour`() {
		assertEquals("42 min", readingTime(Locale.ENGLISH, 42))
	}

	@Test
	fun `whole hours drop the minutes`() {
		assertEquals("2 h", readingTime(Locale.ENGLISH, 120))
	}

	@Test
	fun `hours and minutes`() {
		assertEquals("3 h 5 min", readingTime(Locale.ENGLISH, 185))
	}
}
