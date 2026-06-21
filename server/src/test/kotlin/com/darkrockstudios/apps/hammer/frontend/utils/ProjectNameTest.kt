package com.darkrockstudios.apps.hammer.frontend.utils

import org.junit.jupiter.api.Test
import java.net.URLDecoder
import kotlin.test.assertEquals

class ProjectNameTest {

	// The routing engine percent-decodes the path segment before a handler sees it. URLDecoder
	// reproduces that decode, so encode-then-decode proves the slug round-trips losslessly.
	private fun roundTrip(name: String): String =
		URLDecoder.decode(ProjectName.formatForUrl(name), "UTF-8")

	@Test
	fun `round-trips a plain name`() {
		assertEquals("My Story Name", roundTrip("My Story Name"))
	}

	@Test
	fun `round-trips a name containing a literal dash`() {
		// The old slug turned every dash into a space; this is the case that used to 404.
		assertEquals("Draft 2026-06-07", roundTrip("Draft 2026-06-07"))
	}

	@Test
	fun `round-trips names that are distinct only in dash vs space`() {
		assertEquals("My-Project", roundTrip("My-Project"))
		assertEquals("My Project", roundTrip("My Project"))
		// And the two produce different slugs, so they can no longer collide.
		assertNotEquals(ProjectName.formatForUrl("My-Project"), ProjectName.formatForUrl("My Project"))
	}

	@Test
	fun `round-trips punctuation and symbols`() {
		assertEquals("Alice In Wonderland (# 2 & \"more\")", roundTrip("Alice In Wonderland (# 2 & \"more\")"))
	}

	@Test
	fun `round-trips percent and plus signs`() {
		assertEquals("100% done + extra", roundTrip("100% done + extra"))
	}

	@Test
	fun `round-trips unicode`() {
		assertEquals("Élodie's 夏目漱石", roundTrip("Élodie's 夏目漱石"))
	}

	@Test
	fun `encodes spaces as percent-20 not plus`() {
		assertEquals("My%20Story", ProjectName.formatForUrl("My Story"))
	}

	private fun assertNotEquals(a: String, b: String) =
		kotlin.test.assertTrue(a != b, "expected '$a' != '$b'")
}
