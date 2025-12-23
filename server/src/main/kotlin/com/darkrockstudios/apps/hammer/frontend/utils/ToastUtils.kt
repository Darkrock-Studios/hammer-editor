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
 * Responds with HTML content plus an OOB toast notification.
 * Use this for HTMX endpoints that need to swap content AND show a toast.
 */
suspend fun RoutingContext.respondHtmlWithToast(
	content: String,
	message: String,
	toast: Toast = Toast.Success,
	status: HttpStatusCode = HttpStatusCode.OK
) {
	val toastHtml = toastHtml(message, toast)
	call.respondText(
		text = "$content$toastHtml",
		contentType = ContentType.Text.Html,
		status = status
	)
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
	call.respondText(
		text = toastHtml(message, toast),
		contentType = ContentType.Text.Html,
		status = status
	)
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
	val toastHtml = toastHtml(message, toast)
	call.respondText(
		text = "$content$toastHtml",
		contentType = ContentType.Text.Html,
		status = status
	)
}
