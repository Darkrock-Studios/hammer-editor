package com.darkrockstudios.apps.hammer.common.data.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownContainsTest {

	@Test
	fun `a query matches text stored with escapes`() {
		assertTrue(markdownContains("A well\\-known secret", "well-known"))
		assertTrue(markdownContains("Alice \\(the elder\\) waited", "Alice (the elder)"))
	}

	@Test
	fun `a phrase spanning an escape matches`() {
		assertTrue(markdownContains("the well\\-known road home", "known road"))
	}

	@Test
	fun `matching is case insensitive`() {
		assertTrue(markdownContains("A Well\\-Known secret", "well-known"))
	}

	@Test
	fun `the query is taken literally rather than as markdown`() {
		// Searching the storage form is deliberately unsupported: readers type what they see.
		assertFalse(markdownContains("A well\\-known secret", "well\\-known"))
	}

	@Test
	fun `markers that survive the projection are matched where they sit`() {
		assertTrue(markdownContains("The cost is 5*4 today", "5*4"))
		assertTrue(markdownContains("the user_name field", "user_name"))
	}

	@Test
	fun `markers the projection removes are not findable by typing them`() {
		// The query is literal text matched against the prose on screen, which has no asterisks in it.
		assertFalse(markdownContains("a **Chapter** heading", "**Chapter**"))
		assertFalse(markdownContains("a `code` span", "`code`"))
		assertTrue(markdownContains("a **Chapter** heading", "Chapter heading"))
	}

	@Test
	fun `text that is absent does not match`() {
		assertFalse(markdownContains("A well\\-known secret", "unrelated"))
		assertFalse(markdownContains("", "anything"))
	}
}
