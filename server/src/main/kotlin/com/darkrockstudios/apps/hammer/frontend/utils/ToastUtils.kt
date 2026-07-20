package com.darkrockstudios.apps.hammer.frontend.utils

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter

/**
 * Toast notification types for server-driven OOB swaps
 */
enum class Toast(val cssClass: String, val icon: String) {
	Success("toast-success", "&#10003;"),  // Checkmark
	Warning("toast-warning", "&#9888;"),   // Warning triangle
	Error("toast-error", "&#10007;"),      // X mark
	Info("toast-info", "&#8505;")          // Info symbol
}

/**
 * Generates toast HTML for HTMX out-of-band swap.
 * The toast will be appended to #toast-container and auto-dismissed after 5 seconds.
 */
fun toastHtml(message: String, toast: Toast = Toast.Success): String {
	val escapedMessage = message
		.replace("&", "&amp;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
		.replace("\"", "&quot;")

	return """
		<div id="toast-container" hx-swap-oob="beforeend">
			<div class="toast ${toast.cssClass}" role="alert">
				<span class="toast-icon">${toast.icon}</span>
				<span class="toast-message">$escapedMessage</span>
				<button class="toast-dismiss" aria-label="Dismiss">&times;</button>
			</div>
		</div>
	""".trimIndent()
}

/**
 * Ktor's respond* functions are tail-call suspend functions that hand back the send pipeline's
 * subject (a TextContent, or a CompressedReadChannelResponse once the Compression plugin has run)
 * instead of Unit. With nothing after the call, the compiler emits a tail call and that value is
 * returned to our caller, which casts it to Unit and dies with a ClassCastException whenever the
 * respond lands in a value position — the last expression of a route handler's `when`/`if`.
 * The trailing statement forces a real Unit return. Do not remove it.
 */
private suspend fun RoutingContext.respondHtml(html: String, status: HttpStatusCode) {
	call.respondText(
		text = html,
		contentType = ContentType.Text.Html,
		status = status
	)
	forceUnitReturn()
}

private fun forceUnitReturn() = Unit

/**
 * Responds with HTML content plus an OOB toast notification.
 * Use this for HTMX endpoints that need to swap content AND show a toast.
 */
suspend fun RoutingContext.respondHtmlWithToast(
	content: String,
	message: String,
	toast: Toast = Toast.Success,
	status: HttpStatusCode = HttpStatusCode.OK
) {
	respondHtml("$content${toastHtml(message, toast)}", status)
}

/**
 * Responds with just an OOB toast notification (no main content).
 * Use this for HTMX endpoints that only need to show feedback.
 */
suspend fun RoutingContext.respondToast(
	message: String,
	toast: Toast = Toast.Success,
	status: HttpStatusCode = HttpStatusCode.OK
) {
	respondHtml(toastHtml(message, toast), status)
}

/**
 * Mustache factory for rendering templates to strings.
 */
private val mustacheFactory = DefaultMustacheFactory("templates")

/**
 * Renders a Mustache template to a string.
 */
fun renderTemplate(templatePath: String, model: Map<String, Any?>): String {
	val mustache = mustacheFactory.compile(templatePath)
	val writer = StringWriter()
	mustache.execute(writer, model)
	return writer.toString()
}

/**
 * Responds with rendered Mustache template content plus an OOB toast notification.
 */
suspend fun RoutingContext.respondTemplateWithToast(
	templatePath: String,
	model: Map<String, Any?>,
	message: String,
	toast: Toast = Toast.Success,
	status: HttpStatusCode = HttpStatusCode.OK
) {
	val content = renderTemplate(templatePath, model)
	respondHtml("$content${toastHtml(message, toast)}", status)
}
