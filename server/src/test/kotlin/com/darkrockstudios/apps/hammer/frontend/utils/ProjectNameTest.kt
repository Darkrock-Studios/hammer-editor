package com.darkrockstudios.apps.hammer.frontend.utils

import org.junit.jupiter.api.Test
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectNameTest {

	@Test
	fun `shortId is six base62 characters`() {
		val id = ProjectName.shortId("550e8400-e29b-41d4-a716-446655440000")
		assertEquals(6, id.length)
		assertTrue(id.all { it.isLetterOrDigit() }, "expected base62, got '$id'")
	}

	@Test
	fun `shortId is stable for the same uuid`() {
		val uuid = "550e8400-e29b-41d4-a716-446655440000"
		assertEquals(ProjectName.shortId(uuid), ProjectName.shortId(uuid))
	}

	@Test
	fun `shortId differs for different uuids`() {
		assertNotEquals(ProjectName.shortId("uuid-a"), ProjectName.shortId("uuid-b"))
	}

	@Test
	fun `slug is a pretty dash-joined form with punctuation stripped`() {
		assertEquals("My-Story", ProjectName.slug("My Story"))
		assertEquals("Draft-2026-06-21", ProjectName.slug("Draft 2026-06-21"))
		assertEquals("Alice-In-Wonderland-Name-clash", ProjectName.slug("Alice In Wonderland (# Name clash)"))
	}

	@Test
	fun `slug falls back to a default when nothing usable remains`() {
		assertEquals("project", ProjectName.slug("###"))
	}

	@Test
	fun `the id round-trips out of a generated segment after the router decodes it`() {
		val uuid = "abc-123-def"
		// The router percent-decodes the path segment; URLDecoder reproduces that.
		val segment = URLDecoder.decode(ProjectName.projectSegment("Draft 2026-06-21", uuid), "UTF-8")
		assertEquals(ProjectName.shortId(uuid), ProjectName.idFromSegment(segment))
	}

	@Test
	fun `idFromSegment returns the trailing id and ignores the slug`() {
		assertEquals("7f3k2a", ProjectName.idFromSegment("Draft-2026-06-21-7f3k2a"))
	}

	@Test
	fun `idFromSegment treats a bare id with no slug as the id`() {
		assertEquals("7f3k2a", ProjectName.idFromSegment("7f3k2a"))
	}

	@Test
	fun `penName handle round-trips spaces through dashes`() {
		assertEquals("Jane-Doe", ProjectName.penNameForUrl("Jane Doe"))
		assertEquals("Jane Doe", ProjectName.penNameFromUrl("Jane-Doe"))
	}
}
