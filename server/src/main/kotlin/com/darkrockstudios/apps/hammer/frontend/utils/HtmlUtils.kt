package com.darkrockstudios.apps.hammer.frontend.utils

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML

/**
 * Responds with an HTML fragment built using the kotlinx.html DSL.
 * Useful for HTMX responses that return small HTML snippets.
 *
 * Example usage:
 * ```
 * call.respondHtmlFragment {
 *     input {
 *         id = "myInput"
 *         classes = setOf("form-input")
 *     }
 * }
 *
 * call.respondHtmlFragment {
 *     div("error-message") {
 *         +"Something went wrong"
 *     }
 * }
 * ```
 */
suspend inline fun RoutingCall.respondHtmlFragment(
	status: HttpStatusCode = HttpStatusCode.OK,
	crossinline block: TagConsumer<String>.() -> String
) {
	val html = createHTML().block()
	respondText(html, ContentType.Text.Html, status)
}
