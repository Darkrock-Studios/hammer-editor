package com.darkrockstudios.apps.hammer.frontend

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Guards the `script-src` policy, which carries neither `'unsafe-inline'` nor `'unsafe-eval'`.
 *
 * Everything these tests forbid fails silently in the browser rather than loudly in a build: an
 * inline handler simply never fires. They are the reason the CSP can stay tight as templates grow.
 */
class CspComplianceTest {

	// Read from the source tree rather than the classpath copy: this lints what a contributor
	// edits, and the packaged resources live inside a jar that can't be walked as files.
	private val templates: List<File> = listOf("src/main/resources/templates", "server/src/main/resources/templates")
		.map(::File)
		.firstOrNull { it.isDirectory }
		?.walkTopDown()
		?.filter { it.isFile && it.extension == "mustache" }
		?.toList()
		.orEmpty()

	private val scripts: List<File> = listOf("src/main/resources/assets/js", "server/src/main/resources/assets/js")
		.map(::File)
		.firstOrNull { it.isDirectory }
		?.listFiles { f -> f.extension == "js" }
		?.toList()
		.orEmpty()

	private fun scan(pattern: Regex): List<String> = templates.flatMap { file ->
		file.readLines().withIndex()
			.filter { (_, line) -> pattern.containsMatchIn(line) }
			.map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
	}

	@Test
	fun `templates have templates to scan`() {
		// A path change that silently emptied the corpus would make every other test here vacuous.
		assertTrue(templates.size > 20, "expected the full template corpus, found ${templates.size}")
	}

	@Test
	fun `no template declares an inline event handler`() {
		// No nonce or hash can allow an on* attribute — only 'unsafe-inline' can. Declare handlers
		// with data-on-click/change/input/submit and register them via hammerActions() instead.
		val offenders = scan(Regex("""\son(click|change|input|submit|load|error|focus|blur|keyup|keydown)\s*[=]"""))
		assertTrue(offenders.isEmpty(), "inline event handlers found:\n${offenders.joinToString("\n")}")
	}

	@Test
	fun `no template embeds an executable inline script`() {
		// <script type="application/json"> islands are data, not code, and stay allowed.
		val offenders = templates.flatMap { file ->
			Regex("""<script(?![^>]*\bsrc=)(?![^>]*type="application/(ld\+)?json")[^>]*>""")
				.findAll(file.readText())
				.map { "${file.name}: ${it.value}" }
		}
		assertTrue(offenders.isEmpty(), "inline scripts found:\n${offenders.joinToString("\n")}")
	}

	@Test
	fun `every declared action has a handler, and every handler is used`() {
		// A data-on-* name and its hammerActions() key are matched by string at runtime, so a typo
		// or a rename on one side produces a control that silently does nothing when clicked.
		val declared = templates.flatMap { file ->
			DECLARED_ACTION.findAll(file.readText()).map { it.groupValues[2] }
		}.toSet()

		val registered = scripts.flatMap { file ->
			HANDLER_BLOCK.findAll(file.readText())
				.flatMap { block -> REGISTERED_ACTION.findAll(block.groupValues[1]).map { it.groupValues[1] } }
		}.toSet()

		assertTrue(declared.isNotEmpty() && registered.isNotEmpty(), "found no actions to compare")
		assertTrue((declared - registered).isEmpty(), "declared with no handler: ${declared - registered}")
		assertTrue((registered - declared).isEmpty(), "handler never used: ${registered - declared}")
	}

	@Test
	fun `no template uses an htmx attribute that compiles a string`() {
		// htmx runs hx-on:* bodies and bracketed hx-trigger filters through new Function().
		val offenders = scan(Regex("""hx-on|hx-trigger="[^"]*\[""")) +
			scan(Regex("""hx-(vals|headers)=["']js:"""))
		assertTrue(offenders.isEmpty(), "htmx expressions needing eval found:\n${offenders.joinToString("\n")}")
	}

	private companion object {
		val DECLARED_ACTION = Regex("""data-on-(click|change|input|submit)="([a-z-]+)"""")
		val HANDLER_BLOCK = Regex("""hammerActions\(\{(.*?)}\);""", RegexOption.DOT_MATCHES_ALL)
		val REGISTERED_ACTION = Regex("""'([a-z-]+)'\s*:""")
	}
}
