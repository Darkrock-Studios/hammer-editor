package com.darkrockstudios.apps.hammer.frontend

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * The CSP allows no inline script and no eval, so a template that reintroduces one would ship
 * silently broken. Behaviour belongs in assets/js, wired with data-on-* (see events.js).
 */
class StrictCspTemplatesTest {
	private val templates = File("src/main/resources/templates")
		.walkTopDown()
		.filter { it.isFile && it.extension == "mustache" && !it.path.contains("/email/") }
		.toList()

	private val inlineHandler = Regex("""\son(click|change|input|submit|load|error|focus|blur|key\w+|mouse\w+)\s*=""", RegexOption.IGNORE_CASE)
	private val inlineScript = Regex("""<script(?![^>]*\bsrc=)(?![^>]*type="application/json")[^>]*>""")
	private val evalAttribute = Regex("""hx-on|hx-vals="js:|hx-headers="js:|hx-trigger="[^"]*\[""")

	private fun offenders(pattern: Regex): List<String> = templates.flatMap { file ->
		file.readLines().mapIndexedNotNull { i, line ->
			if (pattern.containsMatchIn(line)) "${file.name}:${i + 1}" else null
		}
	}

	@Test
	fun `templates are found`() {
		assertTrue(templates.isNotEmpty(), "No templates found; is the test running from the server module?")
	}

	@Test
	fun `no inline event handlers`() {
		assertTrue(offenders(inlineHandler).isEmpty(), "Inline handlers: ${offenders(inlineHandler)}")
	}

	@Test
	fun `no inline script blocks other than analytics`() {
		// header.mustache carries the analytics bridge, which the CSP allows by hash.
		val found = offenders(inlineScript).filterNot { it.startsWith("header.mustache") }
		assertTrue(found.isEmpty(), "Inline scripts: $found")
	}

	@Test
	fun `no htmx attributes that need eval`() {
		assertTrue(offenders(evalAttribute).isEmpty(), "Eval attributes: ${offenders(evalAttribute)}")
	}
}
