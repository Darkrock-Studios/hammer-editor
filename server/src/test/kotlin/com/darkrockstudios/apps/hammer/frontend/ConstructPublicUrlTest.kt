package com.darkrockstudios.apps.hammer.frontend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConstructPublicUrlTest {

	@Test
	fun `constructs URL without port when port is 80`() {
		val result = buildPublicUrl(
			scheme = "http",
			host = "example.com",
			port = 80,
			penName = "John Doe",
			projectName = "My Story"
		)

		assertEquals("http://example.com/a/John-Doe/My-Story", result)
	}

	@Test
	fun `constructs URL without port when port is 443`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "John Doe",
			projectName = "My Story"
		)

		assertEquals("https://example.com/a/John-Doe/My-Story", result)
	}

	@Test
	fun `constructs URL with port when port is not 80 or 443`() {
		val result = buildPublicUrl(
			scheme = "http",
			host = "localhost",
			port = 8080,
			penName = "John Doe",
			projectName = "My Story"
		)

		assertEquals("http://localhost:8080/a/John-Doe/My-Story", result)
	}

	@Test
	fun `replaces spaces with dashes in pen name`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "Jane Mary Smith",
			projectName = "Story"
		)

		assertEquals("https://example.com/a/Jane-Mary-Smith/Story", result)
	}

	@Test
	fun `replaces spaces with dashes in project name`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "Author",
			projectName = "The Great Adventure"
		)

		assertEquals("https://example.com/a/Author/The-Great-Adventure", result)
	}

	@Test
	fun `handles pen name and project name with special characters`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "O'Brien",
			projectName = "Story & Tales"
		)

		// URLEncoder encodes special characters: ' -> %27, & -> %26
		assertEquals("https://example.com/a/O%27Brien/Story-%26-Tales", result)
	}

	@Test
	fun `handles single word pen name and project name`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "Author",
			projectName = "Novel"
		)

		assertEquals("https://example.com/a/Author/Novel", result)
	}

	@Test
	fun `handles development server with custom port`() {
		val result = buildPublicUrl(
			scheme = "http",
			host = "127.0.0.1",
			port = 3000,
			penName = "Dev Author",
			projectName = "Test Project"
		)

		assertEquals("http://127.0.0.1:3000/a/Dev-Author/Test-Project", result)
	}

	@Test
	fun `handles subdomain host`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "api.example.com",
			port = 443,
			penName = "Author",
			projectName = "Story"
		)

		assertEquals("https://api.example.com/a/Author/Story", result)
	}

	@Test
	fun `handles unicode characters in names`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "作者",
			projectName = "故事"
		)

		// URLEncoder encodes unicode characters
		assertEquals("https://example.com/a/%E4%BD%9C%E8%80%85/%E6%95%85%E4%BA%8B", result)
	}

	@Test
	fun `handles multiple consecutive spaces`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "John  Doe",
			projectName = "My  Story"
		)

		// Each space becomes a dash
		assertEquals("https://example.com/a/John--Doe/My--Story", result)
	}

	@Test
	fun `handles port 8443 includes port in URL`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 8443,
			penName = "Author",
			projectName = "Story"
		)

		assertEquals("https://example.com:8443/a/Author/Story", result)
	}

	@Test
	fun `handles names with numbers`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "Author123",
			projectName = "Story 2024"
		)

		assertEquals("https://example.com/a/Author123/Story-2024", result)
	}

	@Test
	fun `handles names with hyphens`() {
		val result = buildPublicUrl(
			scheme = "https",
			host = "example.com",
			port = 443,
			penName = "Mary-Jane",
			projectName = "Spider-Man"
		)

		assertEquals("https://example.com/a/Mary-Jane/Spider-Man", result)
	}
}
