package com.darkrockstudios.apps.hammer.base.validate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilenameEncodingTest {
	@Test
	fun `Encodes forbidden characters and trims trailing dots and spaces`() {
		assertEquals("a꞉b？⁄⧹∗＂｜‹›", FilenameEncoding.encode("a:b?/\\*\"|<>"))
		assertEquals("name", FilenameEncoding.encode("name. ."))
	}

	@Test
	fun `Decode inverts encode`() {
		val name = "What? / Why: <A|B> \"x\" *"
		assertEquals(name, FilenameEncoding.decode(FilenameEncoding.encode(name)))
		assertTrue(FilenameEncoding.roundTrips(name))
	}

	@Test
	fun `Lookalike characters do not round trip`() {
		assertEquals("Map:North", FilenameEncoding.decode("Map꞉North"))
		assertFalse(FilenameEncoding.roundTrips("Map꞉North"))
		assertFalse(FilenameEncoding.roundTrips("trailing."))
	}
}
