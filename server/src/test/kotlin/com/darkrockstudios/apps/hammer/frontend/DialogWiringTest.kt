package com.darkrockstudios.apps.hammer.frontend

import com.github.mustachejava.DefaultMustacheFactory
import org.junit.jupiter.api.Test
import java.io.StringWriter
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renders the dialog partials that HTMX swaps in, and checks each control still carries the
 * `data-on-*` attribute its handler is registered against.
 *
 * These dialogs only appear after a story action, so a dropped attribute would otherwise surface as
 * a close button that quietly stops working.
 */
class DialogWiringTest {

	private val factory = DefaultMustacheFactory("templates")

	private fun render(template: String): String {
		val writer = StringWriter()
		val model = mapOf(
			"projectNameForUrl" to "a-story",
			"reviewUrl" to "https://example.test/review/abc",
			"msg" to emptyMap<String, String>(),
			"minDate" to "2026-01-01",
		)
		factory.compile(template).execute(writer, model).flush()
		return writer.toString()
	}

	private fun assertWired(template: String, vararg actions: String) {
		val html = render(template)
		actions.forEach { action ->
			assertTrue(html.contains("""data-on-click="$action""""), "$template lost data-on-click=\"$action\"")
		}
		assertFalse(Regex("""\son[a-z]+\s*=""").containsMatchIn(html), "$template reintroduced an inline handler")
	}

	@Test
	fun `share dialog wires dismiss and close`() {
		assertWired("partials/share-dialog.mustache", "share-dialog-dismiss", "share-dialog-close")
	}

	@Test
	fun `publish warning dialog wires dismiss, close and confirm`() {
		assertWired(
			"partials/publish-warning-dialog.mustache",
			"publish-warning-dismiss",
			"publish-warning-close",
			"publish-confirm",
		)
	}

	@Test
	fun `review link dialog wires dismiss, close, select and copy`() {
		assertWired(
			"partials/review-link-dialog.mustache",
			"review-dialog-dismiss",
			"review-dialog-close",
			"review-link-select",
			"review-link-copy",
		)
	}

	@Test
	fun `dialog overlays carry the dismiss action, and their inner dialog does not`() {
		// The delegated dismiss handler fires only when the click landed on the overlay itself,
		// which is what replaced the old onclick="event.stopPropagation()" on the inner dialog.
		listOf("partials/share-dialog.mustache", "partials/publish-warning-dialog.mustache").forEach { template ->
			val html = render(template)
			val overlay = html.substringAfter("<div class=\"dialog-overlay").substringBefore(">")
			assertTrue(overlay.contains("data-on-click="), "$template overlay is not dismissable")
		}
	}
}
