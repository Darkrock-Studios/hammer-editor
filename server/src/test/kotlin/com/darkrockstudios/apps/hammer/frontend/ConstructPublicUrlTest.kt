package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstructPublicUrlTest {

	private val uuid = "550e8400-e29b-41d4-a716-446655440000"

	private fun build(scheme: String, host: String, port: Int, penName: String, projectName: String) =
		buildPublicUrl(scheme, host, port, penName, projectName, uuid)

	@Test
	fun `omits the port for 80`() {
		val result = build("http", "example.com", 80, "John Doe", "My Story")
		assertEquals("http://example.com/a/John-Doe/${ProjectName.projectSegment("My Story", uuid)}", result)
	}

	@Test
	fun `omits the port for 443`() {
		val result = build("https", "example.com", 443, "John Doe", "My Story")
		assertEquals("https://example.com/a/John-Doe/${ProjectName.projectSegment("My Story", uuid)}", result)
	}

	@Test
	fun `includes a non-default port`() {
		val result = build("http", "localhost", 8080, "John Doe", "My Story")
		assertEquals("http://localhost:8080/a/John-Doe/${ProjectName.projectSegment("My Story", uuid)}", result)
	}

	@Test
	fun `pen name spaces become dashes`() {
		val result = build("https", "example.com", 443, "Jane Mary Smith", "Story")
		assertTrue(result.startsWith("https://example.com/a/Jane-Mary-Smith/"), result)
	}

	@Test
	fun `pen name special characters are percent-encoded`() {
		val result = build("https", "example.com", 443, "O'Brien", "Story")
		assertTrue(result.startsWith("https://example.com/a/O%27Brien/"), result)
	}

	@Test
	fun `project segment carries a decorative slug followed by the short id`() {
		val result = build("https", "example.com", 443, "Author", "The Great Adventure")
		val segment = result.substringAfterLast('/')
		assertTrue(segment.startsWith("The-Great-Adventure-"), segment)
		assertEquals(ProjectName.shortId(uuid), ProjectName.idFromSegment(segment))
	}

	@Test
	fun `bare project name still gets an id suffix`() {
		val result = build("https", "example.com", 443, "Author", "Novel")
		val segment = result.substringAfterLast('/')
		assertEquals("Novel-${ProjectName.shortId(uuid)}", segment)
	}
}
